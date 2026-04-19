package com.devkorea1m.weatherwake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 알람 알림의 액션 버튼(끄기 / 5분 더)을 처리하는 BroadcastReceiver.
 * AlarmService에 ACTION 문자열을 전달해 소리·진동을 멈추거나 스누즈한다.
 */
class AlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action  = intent.action ?: return
        val alarmId = intent.getIntExtra(AlarmService.EXTRA_ALARM_ID_ACTION, -1)

        // 오버레이가 떠 있으면 제거
        AlarmOverlayManager.getInstance(context).removeAlarmOverlay()

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            this.action = action
            putExtra(AlarmService.EXTRA_ALARM_ID_ACTION, alarmId)
        }
        context.startService(serviceIntent)

        // 알림 "끄기" 버튼 → 앱 첫 화면(MainActivity)으로 이동
        if (action == AlarmService.ACTION_DISMISS) {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        }
    }
}
