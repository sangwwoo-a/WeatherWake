package com.devkorea1m.weatherwake.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.devkorea1m.weatherwake.data.db.AppDatabase
import com.devkorea1m.weatherwake.data.model.AlarmEntity
import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.repository.AppResult
import com.devkorea1m.weatherwake.domain.WeatherSnapshot
import com.devkorea1m.weatherwake.runtime.WeatherWakeRuntime
import com.devkorea1m.weatherwake.util.AlarmConstants
import com.devkorea1m.weatherwake.util.AlarmScheduler
import com.devkorea1m.weatherwake.util.DateTimeUtils
import com.devkorea1m.weatherwake.util.LocationHelper
import java.util.concurrent.TimeUnit

private const val TAG = "WeatherCheckWorker"

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

    private val db = AppDatabase.getInstance(context)

    override suspend fun doWork(): Result {
        Log.i(TAG, "▶ doWork enter (alarmId input=${inputData.getInt(KEY_ALARM_ID, -1)})")

        if (!WeatherWakeRuntime.isConfigured()) {
            Log.w(TAG, "◂ EXIT retry: runtime not configured (coldstart race)")
            return Result.retry()
        }
        val provider = WeatherWakeRuntime.weatherProvider

        val alarmId = inputData.getInt(KEY_ALARM_ID, -1)
        if (alarmId == -1) {
            Log.w(TAG, "◂ EXIT failure: alarmId missing")
            return Result.failure()
        }

        val alarm = db.alarmDao().getAlarmById(alarmId)
        if (alarm == null) {
            Log.w(TAG, "◂ EXIT failure: no alarm with id=$alarmId in DB")
            return Result.failure()
        }
        Log.i(TAG, "  alarm loaded: id=${alarm.id} ${alarm.hour}:${alarm.minute} " +
                "isEnabled=${alarm.isEnabled} weatherTrigger=${alarm.weatherTrigger} " +
                "isMoved=${alarm.isMoved} rainSens=${alarm.rainSensitivity} rainAdv=${alarm.rainAdvanceMin}")
        if (!alarm.isEnabled || !alarm.weatherTrigger) {
            Log.w(TAG, "◂ EXIT success: alarm disabled or weatherTrigger off")
            return Result.success()
        }

        val alarmMs = DateTimeUtils.nextAlarmTimeMs(alarm.hour, alarm.minute)
        val now = System.currentTimeMillis()
        Log.i(TAG, "  alarmMs=$alarmMs  now=$now  diff=${(alarmMs - now) / 60000}min")
        if (alarmMs <= now) {
            Log.w(TAG, "◂ EXIT success: alarm time already past")
            return Result.success()
        }

        val latLon = if (LocationHelper.isUseGps(context)) {
            LocationHelper.getCurrentLocation(context) ?: LocationHelper.getSavedLocation(context)
        } else {
            LocationHelper.getSavedLocation(context)
        }
        if (latLon == null) {
            Log.w(TAG, "◂ EXIT retry: no location available")
            return Result.retry()
        }
        Log.i(TAG, "  location: lat=${latLon.lat} lon=${latLon.lon} label=${latLon.label}")

        val override = WeatherWakeRuntime.weatherOverride
        Log.i(TAG, "  override='$override'")
        val weather: WeatherSnapshot = if (override.isNotBlank()) {
            simulatedWeather(override, latLon.label)
        } else {
            // v1.3 부터: 실황 대신 "알람 시각(alarmMs) 의 단기 예보" 조회.
            // 90 분 후 사용자가 실제로 깨어나는 시점의 예보를 기준으로 앞당김 판단.
            Log.i(TAG, "  → calling provider.getForecastAt(alarmMs=$alarmMs)")
            when (val r = provider.getForecastAt(latLon.lat, latLon.lon, alarmMs)) {
                is AppResult.Success      -> {
                    Log.i(TAG, "  forecast SUCCESS: type=${r.data.conditionType} " +
                            "desc='${r.data.description}' rainMmh=${r.data.rainMmh} snowMmh=${r.data.snowMmh}")
                    r.data
                }
                is AppResult.NetworkError -> {
                    Log.w(TAG, "◂ EXIT retry: NetworkError code=${r.code} msg=${r.message}")
                    return Result.retry()
                }
                is AppResult.Error        -> {
                    Log.w(TAG, "◂ EXIT retry: Error ${r.exception?.javaClass?.simpleName} msg=${r.message}")
                    return Result.retry()
                }
            }
        }

        val diffMin = DateTimeUtils.minutesUntilAlarm(alarm.hour, alarm.minute)

        // 실측 강수량 (mm/h). 제공자가 1h 필드를 생략하면 null (이슬비 3xx 등)
        val rainMmh = weather.rainMmh
        val snowMmh = weather.snowMmh

        Log.i(TAG, "  condition=${weather.conditionType} diffMin=$diffMin rainMmh=$rainMmh snowMmh=$snowMmh " +
                "rainAdv=${alarm.rainAdvanceMin} snowAdv=${alarm.snowAdvanceMin} " +
                "rainSens=${alarm.rainSensitivity} snowSens=${alarm.snowSensitivity}")

        when (weather.conditionType) {
            WeatherConditionType.RAIN -> {
                val threshold = rainThreshold(alarm.rainSensitivity)
                val advance   = alarm.rainAdvanceMin
                // mm/h 수치가 있고 > 0 이면 임계값 비교 — "드리즐·혼합강수 같은 소량 강수는
                // 민감도에 따라 거른다" 정책. 그게 아니면 (수치 null, 또는 실측 0 인데 공급자가
                // precipitation 코드를 단언하는 경우) 민감도 "보통(2)" 이하일 때
                // conditionType 만으로도 트리거. "둔감(3)" 은 폭우 원하는 의도이므로 여전히
                // 실측 수치 검증 필수 — 여기선 null 이면 트리거 안 함.
                //
                // KMA 혼합강수(PTY 2/6)와 OWM 이슬비(3xx) 같이 "비가 오지만 mm/h 작게 보고"
                // 되는 케이스에서 보통 민감도 사용자가 앞당김 혜택을 놓치던 문제(v1.7 이전)
                // 해결. Cross-validation 이 RAIN 을 단언하면 일단 깨운다는 safety-first.
                val triggered = if (rainMmh != null && rainMmh > 0f) rainMmh >= threshold
                                else alarm.rainSensitivity <= 2
                Log.i(TAG, "  RAIN branch: threshold=$threshold triggered=$triggered " +
                        "diffMin($diffMin) > advance($advance)? ${diffMin > advance} isMoved=${alarm.isMoved}")
                if (triggered && diffMin > advance && !alarm.isMoved) {
                    Log.i(TAG, "  → ADVANCING alarm by $advance min, persisting isMoved=true")
                    // movedReason 은 UI 에서 resource key 로 매핑되는 안정적 코드로 저장.
                    // (conditionType.name = "RAIN"/"SNOW"). 이전엔 weather.description
                    // 원문을 넣어서 "🧪 시뮬: 강우" 같은 내부 디버그 문구가 UI 에 노출되는
                    // 버그가 있었음.
                    val reasonCode = weather.conditionType.name
                    AlarmScheduler.schedule(context, alarm, advance, reasonCode)
                    db.alarmDao().setMoved(alarm.id, true, reasonCode)
                } else {
                    Log.i(TAG, "  → not advancing (conditions not all met)")
                }
            }
            WeatherConditionType.SNOW -> {
                val threshold = snowThreshold(alarm.snowSensitivity)
                val advance   = alarm.snowAdvanceMin
                // RAIN 과 동일 원칙: 수치 검증은 > 0 인 경우에만. 없거나 0 이면 보통 민감도까지
                // 허용. 가벼운 눈발(KMA RN1="1mm 미만" → 0.1 로 파싱, 또는 OWM snow.1h 생략)
                // 이 통근에 미치는 영향은 실제로 민감도 "보통" 사용자에게 깨울 가치 있음.
                val triggered = if (snowMmh != null && snowMmh > 0f) snowMmh >= threshold
                                else alarm.snowSensitivity <= 2
                Log.i(TAG, "  SNOW branch: threshold=$threshold triggered=$triggered " +
                        "diffMin($diffMin) > advance($advance)? ${diffMin > advance} isMoved=${alarm.isMoved}")
                if (triggered && diffMin > advance && !alarm.isMoved) {
                    Log.i(TAG, "  → ADVANCING alarm by $advance min, persisting isMoved=true")
                    // movedReason 은 UI 에서 resource key 로 매핑되는 안정적 코드로 저장.
                    // (conditionType.name = "RAIN"/"SNOW"). 이전엔 weather.description
                    // 원문을 넣어서 "🧪 시뮬: 강우" 같은 내부 디버그 문구가 UI 에 노출되는
                    // 버그가 있었음.
                    val reasonCode = weather.conditionType.name
                    AlarmScheduler.schedule(context, alarm, advance, reasonCode)
                    db.alarmDao().setMoved(alarm.id, true, reasonCode)
                } else {
                    Log.i(TAG, "  → not advancing (conditions not all met)")
                }
            }
            WeatherConditionType.CLEAR -> {
                Log.i(TAG, "  CLEAR branch: isMoved=${alarm.isMoved} → " +
                        if (alarm.isMoved) "restoring original time" else "no-op")
                if (alarm.isMoved) {
                    AlarmScheduler.schedule(context, alarm, 0)
                    db.alarmDao().setMoved(alarm.id, false, "")
                }
            }
        }

        Log.i(TAG, "◂ EXIT success: normal completion")
        return Result.success()
    }

    /**
     * 디버그 전용 — 외부 API 호출 없이 가상의 날씨를 만들어 준다.
     * 사용자 민감도 임계값을 확실히 넘는 값으로 세팅해서 앞당김이 무조건 발동되도록 함.
     */
    private fun simulatedWeather(kind: String, cityName: String): WeatherSnapshot = when (kind.uppercase()) {
        "RAIN"  -> WeatherSnapshot(
            conditionType = WeatherConditionType.RAIN,
            description   = "🧪 시뮬: 강우",
            tempCelsius   = 12.0,
            cityName      = cityName,
            rainMmh       = 5.0f,   // "둔감" 임계값(3.0) 보다도 높게
            snowMmh       = null
        )
        "SNOW"  -> WeatherSnapshot(
            conditionType = WeatherConditionType.SNOW,
            description   = "🧪 시뮬: 강설",
            tempCelsius   = -2.0,
            cityName      = cityName,
            rainMmh       = null,
            snowMmh       = 12.0f   // "둔감" 임계값(10.0) 보다도 높게
        )
        else    -> WeatherSnapshot(
            conditionType = WeatherConditionType.CLEAR,
            description   = "🧪 시뮬: 맑음",
            tempCelsius   = 18.0,
            cityName      = cityName,
            rainMmh       = null,
            snowMmh       = null
        )
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
