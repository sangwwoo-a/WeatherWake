package com.devkorea1m.weatherwake.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.devkorea1m.weatherwake.AlarmReceiver
import com.devkorea1m.weatherwake.data.model.AlarmEntity
import java.util.Calendar

object AlarmScheduler {

    const val EXTRA_ALARM_ID     = "alarm_id"
    const val EXTRA_MOVED_REASON = "moved_reason"
    const val EXTRA_IS_MOVED     = "is_moved"
    const val EXTRA_SOUND_URI    = "sound_uri"

    /**
     * 알람 등록 (또는 갱신)
     * @param advanceMinutes 앞당길 분 (0이면 원래 시각)
     */
    fun schedule(
        context: Context,
        alarm: AlarmEntity,
        advanceMinutes: Int = 0,
        movedReason: String = ""
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = buildIntent(context, alarm, movedReason, advanceMinutes > 0)
        val pendingIntent = PendingIntent.getBroadcast(
            context, alarm.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = calcTriggerTime(alarm.hour, alarm.minute, advanceMinutes)
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
            pendingIntent
        )
    }

    /** 알람 취소 */
    fun cancel(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, alarmId, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
    }

    /** 다음 알람 울릴 시각 계산 (밀리초) */
    private fun calcTriggerTime(hour: Int, minute: Int, advanceMinutes: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -advanceMinutes)
            // 이미 지난 시각이면 내일로
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    private fun buildIntent(
        context: Context,
        alarm: AlarmEntity,
        movedReason: String,
        isMoved: Boolean
    ) = Intent(context, AlarmReceiver::class.java).apply {
        putExtra(EXTRA_ALARM_ID, alarm.id)
        putExtra(EXTRA_MOVED_REASON, movedReason)
        putExtra(EXTRA_IS_MOVED, isMoved)
        putExtra(EXTRA_SOUND_URI, alarm.soundUri)
    }
}
