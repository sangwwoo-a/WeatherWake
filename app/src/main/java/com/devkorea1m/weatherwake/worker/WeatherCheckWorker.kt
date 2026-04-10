package com.devkorea1m.weatherwake.worker

import android.content.Context
import androidx.work.*
import com.devkorea1m.weatherwake.BuildConfig
import com.devkorea1m.weatherwake.data.db.AppDatabase
import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.repository.WeatherRepository
import com.devkorea1m.weatherwake.util.AlarmScheduler
import com.devkorea1m.weatherwake.util.LocationHelper
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 30분마다 실시간 날씨를 확인하고 AlarmManager를 갱신하는 Worker
 *
 * 로직:
 * 1. GPS 또는 저장된 위치로 현재 날씨 API 호출
 * 2. 비/눈 감지 → 오늘 활성 알람을 앞당겨 재등록
 * 3. 맑음 감지 & 이미 앞당겨진 알람 → 원래 시각으로 복원
 * 4. 알람까지 앞당길 시간보다 여유가 없으면 → 그대로 유지
 */
class WeatherCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = WeatherRepository()
    private val db = AppDatabase.getInstance(context)

    override suspend fun doWork(): Result {
        // 위치 가져오기
        val latLon = if (LocationHelper.isUseGps(context)) {
            LocationHelper.getCurrentLocation(context) ?: LocationHelper.getSavedLocation(context)
        } else {
            LocationHelper.getSavedLocation(context)
        } ?: return Result.failure()   // 위치 없으면 중단

        // 날씨 API 호출
        val weather = repository.getCurrentWeather(
            lat = latLon.lat,
            lon = latLon.lon,
            apiKey = BuildConfig.OWM_API_KEY
        ) ?: return Result.retry()     // 네트워크 오류 → 재시도

        val alarms = db.alarmDao().getActiveWeatherAlarms()
        val nowMs = System.currentTimeMillis()

        for (alarm in alarms) {
            val alarmMs = nextAlarmTimeMs(alarm.hour, alarm.minute)
            val diffMin = (alarmMs - nowMs) / 60_000

            when (weather.conditionType) {
                WeatherConditionType.RAIN -> {
                    val advance = alarm.rainAdvanceMin
                    if (diffMin > advance && !alarm.isMoved) {
                        // 아직 충분한 시간 & 아직 앞당기지 않은 경우 → 앞당김
                        AlarmScheduler.schedule(context, alarm, advance, weather.description)
                        db.alarmDao().setMoved(alarm.id, true, weather.description)
                    }
                }
                WeatherConditionType.SNOW -> {
                    val advance = alarm.snowAdvanceMin
                    if (diffMin > advance && !alarm.isMoved) {
                        AlarmScheduler.schedule(context, alarm, advance, weather.description)
                        db.alarmDao().setMoved(alarm.id, true, weather.description)
                    }
                }
                WeatherConditionType.CLEAR -> {
                    if (alarm.isMoved) {
                        // 날씨가 개었고 앞당겨진 상태 → 원래 시각으로 복원
                        AlarmScheduler.schedule(context, alarm, 0)
                        db.alarmDao().setMoved(alarm.id, false, "")
                    }
                }
            }
        }

        return Result.success()
    }

    private fun nextAlarmTimeMs(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    companion object {
        const val WORK_NAME = "weather_check_periodic"

        /** 30분 주기 PeriodicWork 등록 */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherCheckWorker>(
                30, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,   // 이미 등록돼 있으면 유지
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
