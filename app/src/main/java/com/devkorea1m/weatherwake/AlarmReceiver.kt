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

/**
 * AlarmManager 브로드캐스트 수신 → 알람 화면 직접 기동 + AlarmService 시작.
 *
 * 알람 UI 표시에 두 경로를 병행한다(SYSTEM_ALERT_WINDOW 오버레이는 Play 스토어 리스크라 제외):
 *
 *   A) context.startActivity(AlarmRingActivity)     — setAlarmClock 의 showIntent 예외 덕분에
 *                                                     백그라운드/잠금 상태에서도 Activity 기동 허용.
 *                                                     Samsung Edge Lighting 등이 fullScreenIntent 를
 *                                                     가로채는 기기에서도 확실히 UI 를 띄우기 위해 필요.
 *   B) AlarmService foreground notification 의 fullScreenIntent — A 가 막힌 경우의 백업.
 *
 * AlarmRingActivity 는 singleTop 이라 A/B 둘 다 성공해도 중복 인스턴스가 생기지 않는다.
 *
 * 반복 알람은 setAlarmClock 한 번만 예약되므로 이 Receiver 가 발동되면
 * 다음 발동(다음 반복 요일)을 자동으로 재예약한다.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId     = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val isMoved     = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_MOVED, false)
        val movedReason = intent.getStringExtra(AlarmScheduler.EXTRA_MOVED_REASON) ?: ""
        val soundUri    = intent.getStringExtra(AlarmScheduler.EXTRA_SOUND_URI) ?: ""
        val repeatDays  = intent.getIntExtra(AlarmScheduler.EXTRA_REPEAT_DAYS, -1)

        // 서비스가 foreground 로 전환되고 소리를 켤 동안 CPU 를 유지.
        // 화면 켜기는 fullScreenIntent + AlarmRingActivity 의 setTurnScreenOn 담당이므로
        // 여기서는 PARTIAL_WAKE_LOCK 만 사용 (SCREEN_BRIGHT_WAKE_LOCK 은 deprecated).
        @Suppress("DEPRECATION")
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WeatherWake:AlarmReceiver")
        wl.acquire(10_000L)

        // Android 15 에서 Receiver 의 직접 startActivity 는 BAL_BLOCK 으로 "invisible launch" 됨.
        // 더 큰 문제: invisible Activity 가 pending 상태로 큐에 들어가면 시스템이 fullScreenIntent 를
        // "이미 Activity 실행 중" 으로 판단해 생략해버림. 따라서 startActivity 직접 호출은 제거.
        // AlarmService 의 notification fullScreenIntent 만으로 AlarmRingActivity 를 띄운다.
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
        // setAlarmClock 은 1회성이므로 재예약하지 않으면 다음 날 알람이 안 울림.
        // 1회성(repeatDays == 0)은 AlarmService.dismissAlarm 에서 isEnabled=false 로 처리.
        if (alarmId >= 0 && repeatDays != 0) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AppDatabase.getInstance(context).alarmDao()
                    val alarm = dao.getAlarmById(alarmId)
                    if (alarm != null && alarm.isEnabled) {
                        // 원래 시각으로 재예약(앞당김 해제). 날씨 Worker 가 다시 판단해서 필요 시 앞당김.
                        dao.setMoved(alarm.id, false, "")
                        AlarmScheduler.schedule(
                            context,
                            alarm.copy(isMoved = false, movedReason = ""),
                            0
                        )
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
