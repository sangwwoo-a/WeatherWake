package com.devkorea1m.weatherwake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.devkorea1m.weatherwake.util.AlarmScheduler

/**
 * AlarmManager 브로드캐스트 수신
 * → AlarmRingActivity 실행
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId     = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val movedReason = intent.getStringExtra(AlarmScheduler.EXTRA_MOVED_REASON) ?: ""
        val isMoved     = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_MOVED, false)
        val soundUri    = intent.getStringExtra(AlarmScheduler.EXTRA_SOUND_URI) ?: ""

        val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_MOVED_REASON, movedReason)
            putExtra(AlarmScheduler.EXTRA_IS_MOVED, isMoved)
            putExtra(AlarmScheduler.EXTRA_SOUND_URI, soundUri)
        }
        context.startActivity(ringIntent)
    }
}
