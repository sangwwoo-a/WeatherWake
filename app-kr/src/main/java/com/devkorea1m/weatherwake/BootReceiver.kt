package com.devkorea1m.weatherwake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.devkorea1m.weatherwake.data.db.AppDatabase
import com.devkorea1m.weatherwake.util.AlarmScheduler
import com.devkorea1m.weatherwake.worker.WeatherCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 기기 재시작 시 AlarmManager + WeatherCheckWorker 복원
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val db     = AppDatabase.getInstance(context)
            val alarms = db.alarmDao().getActiveWeatherAlarms()

            for (alarm in alarms) {
                // isMoved 초기화 (재시작 시 날씨를 다시 판단)
                db.alarmDao().setMoved(alarm.id, false, "")
                // 알람 원래 시각으로 복원
                AlarmScheduler.schedule(context, alarm, 0)
                // 알람 90분 전 날씨 체크 1회 재예약 (30분 주기 대신)
                WeatherCheckWorker.scheduleFor(context, alarm)
            }
        }
    }
}
