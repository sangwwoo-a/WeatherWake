package com.devkorea1m.weatherwake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.devkorea1m.weatherwake.data.db.AppDatabase
import com.devkorea1m.weatherwake.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * AlarmManager 브로드캐스트 수신 → AlarmService 시작.
 *
 * 표준 Android 경로만 사용한다:
 *   ① PARTIAL_WAKE_LOCK 으로 CPU 유지 (서비스 FG 전환 동안)
 *   ② startForegroundService(AlarmService) — ALARM_MANAGER_ALARM_CLOCK temp-allow-list 덕분에 백그라운드에서도 허용
 *   ③ AlarmService 의 notification fullScreenIntent 가 AlarmRingActivity 를 잠금화면 위에 띄움
 *
 * Receiver 에서 직접 startActivity 는 호출하지 않는다 — Android 15 BAL 로 "invisible launch" 되면
 * 시스템이 fullScreenIntent 까지 "Activity already pending" 으로 스킵한다.
 *
 * setAlarmClock 은 1회성이므로 이 Receiver 가 발동되면 다음 발동(다음 반복 요일)을 자동 재예약한다.
 * 재예약 시 fromMs = "오늘 원래 시각 + 1분" 으로 넘겨서, 비 감지로 앞당겨 울린 날에도
 * "오늘 원래 시각" 에 두 번째 알람이 울리지 않도록 한다.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId     = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val isMoved     = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_MOVED, false)
        val movedReason = intent.getStringExtra(AlarmScheduler.EXTRA_MOVED_REASON) ?: ""
        val soundUri    = intent.getStringExtra(AlarmScheduler.EXTRA_SOUND_URI) ?: ""
        val repeatDays  = intent.getIntExtra(AlarmScheduler.EXTRA_REPEAT_DAYS, -1)

        @Suppress("DEPRECATION")
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WeatherWake:AlarmReceiver")
        wl.acquire(10_000L)

        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_MOVED_REASON, movedReason)
                putExtra(AlarmScheduler.EXTRA_IS_MOVED, isMoved)
                putExtra(AlarmScheduler.EXTRA_SOUND_URI, soundUri)
                putExtra(AlarmScheduler.EXTRA_REPEAT_DAYS, repeatDays)
            }
        )

        // 반복 알람이면 다음 발동을 자동 재예약.
        // 1회성(repeatDays == 0)은 AlarmService.dismissAlarm 에서 isEnabled=false 로 처리.
        if (alarmId >= 0 && repeatDays != 0) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AppDatabase.getInstance(context).alarmDao()
                    val alarm = dao.getAlarmById(alarmId)
                    if (alarm != null && alarm.isEnabled) {
                        // 앞당김 상태 초기화 후 다음 발동을 원래 시각으로 재예약.
                        // 날씨 Worker 가 다음 날 90분 전에 다시 판단해서 필요 시 또 앞당김.
                        dao.setMoved(alarm.id, false, "")

                        // fromMs = 오늘 원래 시각 + 1분:
                        // 비 감지 앞당김으로 06:30 에 이 Receiver 가 발동된 경우, 재예약이
                        // "오늘 07:00" 로 되면 알람이 오늘 하루에 두 번 울리는 버그가 생긴다.
                        // "오늘 원래 시각" 을 이미 소비된 시점으로 간주하고 그 이후에서 탐색.
                        val originalTodayPlus1 = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, alarm.hour)
                            set(Calendar.MINUTE, alarm.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                            add(Calendar.MINUTE, 1)
                        }.timeInMillis

                        AlarmScheduler.schedule(
                            context = context,
                            alarm = alarm.copy(isMoved = false, movedReason = ""),
                            advanceMinutes = 0,
                            movedReason = "",
                            fromMs = originalTodayPlus1
                        )
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
