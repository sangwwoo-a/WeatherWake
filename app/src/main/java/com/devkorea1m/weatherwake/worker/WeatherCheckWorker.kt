package com.devkorea1m.weatherwake.worker

import android.content.Context
import androidx.work.*
import com.devkorea1m.weatherwake.BuildConfig
import com.devkorea1m.weatherwake.data.db.AppDatabase
import com.devkorea1m.weatherwake.data.model.AlarmEntity
import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.repository.WeatherRepository
import com.devkorea1m.weatherwake.util.AlarmConstants
import com.devkorea1m.weatherwake.util.AlarmScheduler
import com.devkorea1m.weatherwake.util.DateTimeUtils
import com.devkorea1m.weatherwake.util.LocationHelper
import java.util.concurrent.TimeUnit

/**
 * 알람 시각 [WEATHER_CHECK_BEFORE_MIN]분 전에 날씨를 확인하고 AlarmManager를 조정하는 Worker
 *
 * 로직:
 * 1. 알람 ID를 InputData로 받아 해당 알람만 처리
 * 2. GPS 또는 저장된 위치로 현재 날씨 API 호출
 * 3. 비/눈 감지 → 알람을 앞당겨 재등록
 * 4. 맑음 & 이미 앞당겨진 상태 → 원래 시각으로 복원
 * 5. 위치/네트워크 실패 시 → Result.retry() → WorkManager가 재시도
 * 6. 알람 시각이 이미 지난 경우 → Result.success()로 종료
 */
class WeatherCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = WeatherRepository()
    private val db = AppDatabase.getInstance(context)

    override suspend fun doWork(): Result {
        val alarmId = inputData.getInt(KEY_ALARM_ID, -1)
        if (alarmId == -1) return Result.failure()

        val alarm = db.alarmDao().getAlarmById(alarmId) ?: return Result.failure()
        if (!alarm.isEnabled || !alarm.weatherTrigger) return Result.success()

        // 알람 시각이 이미 지났으면 재시도 없이 종료
        val alarmMs = DateTimeUtils.nextAlarmTimeMs(alarm.hour, alarm.minute)
        if (alarmMs <= System.currentTimeMillis()) return Result.success()

        // 위치 가져오기 (실패 시 재시도)
        val latLon = if (LocationHelper.isUseGps(context)) {
            LocationHelper.getCurrentLocation(context) ?: LocationHelper.getSavedLocation(context)
        } else {
            LocationHelper.getSavedLocation(context)
        } ?: return Result.retry()

        // 날씨 API 호출 (실패 시 재시도)
        val weatherResult = repository.getCurrentWeather(
            lat    = latLon.lat,
            lon    = latLon.lon,
            apiKey = BuildConfig.OWM_API_KEY
        )

        val weather = when (weatherResult) {
            is com.devkorea1m.weatherwake.data.repository.AppResult.Success -> weatherResult.data
            is com.devkorea1m.weatherwake.data.repository.AppResult.NetworkError -> return Result.retry()
            is com.devkorea1m.weatherwake.data.repository.AppResult.Error -> return Result.retry()
        }

        val diffMin = DateTimeUtils.minutesUntilAlarm(alarm.hour, alarm.minute)

        // 실측 강수량 (mm/h). API가 rain/snow 블록을 생략하는 경우가 있음 (이슬비 3xx 등)
        val rainMmh = weather.rain?.oneHour
        val snowMmh = weather.snow?.oneHour

        when (weather.conditionType) {
            WeatherConditionType.RAIN -> {
                val threshold = rainThreshold(alarm.rainSensitivity)
                val advance   = alarm.rainAdvanceMin
                // rain.1h 수치가 있으면 임계값 비교, 없으면(이슬비 등 OWM 생략) 민감도가
                // 아주민감(0) 또는 민감(1)일 때만 conditionType 코드만으로도 트리거
                val triggered = if (rainMmh != null) rainMmh >= threshold
                                else alarm.rainSensitivity <= 1
                if (triggered && diffMin > advance && !alarm.isMoved) {
                    AlarmScheduler.schedule(context, alarm, advance, weather.description)
                    db.alarmDao().setMoved(alarm.id, true, weather.description)
                }
            }
            WeatherConditionType.SNOW -> {
                val threshold = snowThreshold(alarm.snowSensitivity)
                val advance   = alarm.snowAdvanceMin
                // snow.1h 수치가 있으면 임계값 비교, 없으면 민감도가 아주민감(0)/민감(1)일 때 트리거
                val triggered = if (snowMmh != null) snowMmh >= threshold
                                else threshold <= 1.0f
                if (triggered && diffMin > advance && !alarm.isMoved) {
                    AlarmScheduler.schedule(context, alarm, advance, weather.description)
                    db.alarmDao().setMoved(alarm.id, true, weather.description)
                }
            }
            WeatherConditionType.CLEAR -> {
                if (alarm.isMoved) {
                    AlarmScheduler.schedule(context, alarm, 0)
                    db.alarmDao().setMoved(alarm.id, false, "")
                }
            }
        }

        return Result.success()
    }

    companion object {
        const val KEY_ALARM_ID = "alarm_id"

        /**
         * 비 민감도 → 임계 강수량 (mm/h) — 한국 우산 체감 기준
         * 0=아주민감(0.1) 1=민감(0.5) 2=보통(1.0) 3=둔감(3.0)
         */
        fun rainThreshold(sensitivity: Int): Float = when (sensitivity) {
            0    -> 0.1f
            1    -> 0.5f
            3    -> 3.0f
            else -> 1.0f   // 2=보통(기본)
        }

        /**
         * 눈 민감도 → 임계 적설량 (mm/h 기준, 1cm ≈ 1mm water equiv.)
         * 0=아주민감(0.5) 1=민감(1.0) 2=보통(3.0) 3=둔감(10.0)
         */
        fun snowThreshold(sensitivity: Int): Float = when (sensitivity) {
            0    -> 0.5f
            1    -> 1.0f
            3    -> 10.0f
            else -> 3.0f   // 2=보통(기본)
        }
        private const val WORK_NAME_PREFIX = "weather_check_alarm_"

        /**
         * 알람 [WEATHER_CHECK_BEFORE_MIN]분 전에 날씨 체크 1회 예약.
         * 실패 시 WorkManager가 [WORKER_BACKOFF_MIN]분 간격으로 재시도.
         */
        fun scheduleFor(context: Context, alarm: AlarmEntity) {
            if (!alarm.isEnabled || !alarm.weatherTrigger) return

            val alarmMs = DateTimeUtils.nextAlarmTimeMs(alarm.hour, alarm.minute)
            val checkMs = alarmMs - TimeUnit.MINUTES.toMillis(AlarmConstants.WEATHER_CHECK_BEFORE_MIN)
            val delayMs = (checkMs - System.currentTimeMillis()).coerceAtLeast(0L)

            val request = OneTimeWorkRequestBuilder<WeatherCheckWorker>()
                .setInputData(workDataOf(KEY_ALARM_ID to alarm.id))
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, AlarmConstants.WORKER_BACKOFF_MIN, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(alarm.id),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelFor(context: Context, alarmId: Int) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(alarmId))
        }

        private fun workName(alarmId: Int) = "$WORK_NAME_PREFIX$alarmId"
    }
}
