package com.devkorea1m.weatherwake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.devkorea1m.weatherwake.util.AlarmScheduler

/**
 * AlarmManager 브로드캐스트 수신 → 화면 켜기 + 알람 UI 표시 + AlarmService 시작
 *
 * 화면이 꺼진 상태에서도 알람 UI가 뜨도록 4가지 메커니즘을 **동시에** 사용한다.
 * 각 OEM(삼성/샤오미 등)이 일부를 차단해도 나머지가 동작하면 성공:
 *
 *   ① SCREEN_BRIGHT_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP  (deprecated 이지만 일부 기기에서 유효)
 *   ② WindowManager 오버레이 + FLAG_TURN_SCREEN_ON      (SYSTEM_ALERT_WINDOW 권한 필요)
 *   ③ AlarmRingActivity 직접 시작 + setTurnScreenOn(true) (setAlarmClock 예외 조건)
 *   ④ AlarmService notification fullScreenIntent         (시스템이 처리)
 *
 * AlarmRingActivity 는 singleTop 이므로 ②③④ 중 복수가 성공해도 중복 인스턴스가 생기지 않는다.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId     = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val isMoved     = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_MOVED, false)
        val movedReason = intent.getStringExtra(AlarmScheduler.EXTRA_MOVED_REASON) ?: ""
        val soundUri    = intent.getStringExtra(AlarmScheduler.EXTRA_SOUND_URI) ?: ""
        val repeatDays  = intent.getIntExtra(AlarmScheduler.EXTRA_REPEAT_DAYS, -1)

        // ── ① WakeLock: 화면 강제 켜기 ───────────────────────────────────
        @Suppress("DEPRECATION")
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WeatherWake:AlarmReceiver"
        )
        wl.acquire(15_000L)

        // ── ② 오버레이: WindowManager 로 잠금/충전 화면 위에 직접 표시 ──────
        val overlayManager = AlarmOverlayManager.getInstance(context)
        if (overlayManager.canDrawOverlays()) {
            overlayManager.showAlarmOverlay(alarmId, isMoved, movedReason)
        }

        // ── ③ Activity 직접 시작: setTurnScreenOn + setShowWhenLocked ────
        try {
            context.startActivity(
                Intent(context, AlarmRingActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    )
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmScheduler.EXTRA_IS_MOVED, isMoved)
                    putExtra(AlarmScheduler.EXTRA_MOVED_REASON, movedReason)
                }
            )
        } catch (_: Exception) { /* 백그라운드 Activity 시작 차단 시 무시 */ }

        // ── ④ AlarmService 시작 → notification fullScreenIntent (시스템 처리) ─
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
    }
}
