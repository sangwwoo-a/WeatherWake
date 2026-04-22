package com.devkorea1m.weatherwake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 알람 알림의 액션 버튼(끄기 / 5분 더)을 처리하는 BroadcastReceiver.
 * AlarmService 에 ACTION 문자열을 전달해 소리·진동을 멈추거나 스누즈한다.
 *
 * MainActivity 로의 이동은 AlarmRingActivity.finishReceiver 가 담당한다.
 * 여기서 startActivity(MainActivity) 를 직접 호출하면 Android 15 BAL 로 차단되므로
 * (isPendingIntent=false → BAL_BLOCK) 그 경로는 사용하지 않는다.
 */
class AlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action  = intent.action ?: return
        val alarmId = intent.getIntExtra(AlarmService.EXTRA_ALARM_ID_ACTION, -1)

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            this.action = action
            putExtra(AlarmService.EXTRA_ALARM_ID_ACTION, alarmId)
        }
        context.startService(serviceIntent)
    }
}
