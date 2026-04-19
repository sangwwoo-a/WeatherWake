package com.devkorea1m.weatherwake.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.devkorea1m.weatherwake.AlarmReceiver
import com.devkorea1m.weatherwake.MainActivity
import com.devkorea1m.weatherwake.data.model.AlarmEntity
import com.devkorea1m.weatherwake.worker.WeatherCheckWorker

object AlarmScheduler {

    const val EXTRA_ALARM_ID     = "alarm_id"
    const val EXTRA_MOVED_REASON = "moved_reason"
    const val EXTRA_IS_MOVED     = "is_moved"
    const val EXTRA_SOUND_URI    = "sound_uri"
    const val EXTRA_REPEAT_DAYS  = "repeat_days"

    /**
     * 알람 등록 (또는 갱신).
     *
     * 날씨 연동이 켜진 알람이면 "알람 시각 90분 전" WeatherCheckWorker 도 같이 (재)예약한다.
     * AlarmReceiver 가 발동 후 이 함수로 다음 날 알람을 재예약할 때도 Worker 가 같이 등록돼야
     * 다음 날 날씨 감지 앞당김이 동작한다.
     *
     * @param advanceMinutes 앞당길 분 (0이면 원래 시각)
     * @param fromMs         "이 시각 이후"의 첫 발동을 찾는다. 알람이 방금 울렸다면
     *                       (오늘 원래 시각 + 1분) 을 넘겨 오늘 원래 시각 중복 발동을 방지.
     */
    fun schedule(
        context: Context,
        alarm: AlarmEntity,
        advanceMinutes: Int = 0,
        movedReason: String = "",
        fromMs: Long = System.currentTimeMillis()
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = buildIntent(context, alarm, movedReason, advanceMinutes > 0)

        // 알람 발동 시 AlarmReceiver로 브로드캐스트를 보내는 실제 operation
        val operationPi = PendingIntent.getBroadcast(
            context, alarm.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 상태바 알람 아이콘을 탭했을 때 열리는 showIntent.
        // 반드시 Activity PendingIntent여야 한다:
        //  - BroadcastReceiver를 넣으면 일부 기기에서 setAlarmClock()의
        //    "백그라운드 Activity 시작 허용" 예외 조건이 제대로 적용되지 않는다.
        //  - 이 showIntent 덕분에 시스템이 이 앱을 "알람 시계 앱"으로 인식하고
        //    화면이 꺼진 상태에서도 AlarmReceiver → startActivity() 및
        //    fullScreenIntent 발동을 허용한다.
        val showPi = PendingIntent.getActivity(
            context,
            alarm.id + 50000,  // operationPi와 requestCode 충돌 방지
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = DateTimeUtils.nextAlarmTimeMs(
            hour           = alarm.hour,
            minute         = alarm.minute,
            repeatDays     = alarm.repeatDays,
            advanceMinutes = advanceMinutes,
            fromMs         = fromMs
        )

        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, showPi),  // showIntent = Activity
                operationPi                                          // operation  = BroadcastReceiver
            )
        } catch (e: SecurityException) {
            // Android 12+에서 SCHEDULE_EXACT_ALARM 권한 미승인 시 발생
            e.printStackTrace()
        }

        // 날씨 연동 알람이면 해당 알람의 다음 "90분 전 날씨 체크" Worker 도 같이 재예약.
        // (AlarmReceiver 가 발동 후 이 함수를 부를 때도 다음 날 Worker 가 함께 예약됨.)
        if (alarm.isEnabled && alarm.weatherTrigger) {
            WeatherCheckWorker.scheduleFor(context, alarm)
        }
    }

    /** 알람 취소 — 90분 전 날씨 체크 Worker도 같이 취소 */
    fun cancel(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, alarmId, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) alarmManager.cancel(pendingIntent)
        WeatherCheckWorker.cancelFor(context, alarmId)
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
        putExtra(EXTRA_REPEAT_DAYS, alarm.repeatDays)
    }
}
