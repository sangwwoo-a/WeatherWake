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
 * 기기 재시작 시 WorkManager + AlarmManager 복원
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // 1. WorkManager 30분 주기 재등록
        WeatherCheckWorker.enqueue(context)

        // 2. Room DB에서 활성 알람 복원
        CoroutineScope(Dispatchers.IO).launch {
            val alarms = AppDatabase.getInstance(context).alarmDao().getActiveWeatherAlarms()
            for (alarm in alarms) {
                // isMoved 상태 초기화 (재시작 시 날씨를 다시 판단)
                AppDatabase.getInstance(context).alarmDao().setMoved(alarm.id, false, "")
                AlarmScheduler.schedule(context, alarm, 0)
            }
        }
    }
}
