package com.devkorea1m.weatherwake.data.repository

import com.devkorea1m.weatherwake.data.model.ForecastEntry
import com.devkorea1m.weatherwake.data.model.ForecastResponse
import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.model.WeatherResponse
import com.devkorea1m.weatherwake.data.network.RetrofitClient
import com.devkorea1m.weatherwake.domain.WeatherProvider
import com.devkorea1m.weatherwake.domain.WeatherSnapshot
import com.devkorea1m.weatherwake.domain.WeatherSource
import retrofit2.HttpException
import java.io.IOException

/**
 * OpenWeatherMap 기반 [WeatherProvider] 구현.
 *
 * API 키는 생성 시점에 주입받는다. 호출부는 [WeatherProvider] 인터페이스만
 * 알면 되므로 이후 KMA/NWS 공급자 또는 교차검증 아그리게이터로 쉽게 교체 가능.
 *
 * - [getCurrentWeather]: `/data/2.5/weather` (실황)
 * - [getForecastAt]: `/data/2.5/forecast` (5-day / 3-hour forecast)
 */
class WeatherRepository(
    private val apiKey: String
) : WeatherProvider {

    override suspend fun getCurrentWeather(
        lat: Double,
        lon: Double
    ): AppResult<WeatherSnapshot> {
        return try {
            val response = RetrofitClient.weatherApi
                .getCurrentWeather(lat, lon, apiKey, "metric", "kr")
            AppResult.Success(response.toSnapshot())
        } catch (e: HttpException) {
            AppResult.NetworkError(e.code(), "날씨 서버 오류 (${e.code()})")
        } catch (e: IOException) {
            AppResult.Error(e, "네트워크 연결을 확인해주세요")
        } catch (e: Exception) {
            AppResult.Error(e, "날씨 정보를 불러올 수 없어요")
        }
    }

    override suspend fun getForecastAt(
        lat: Double,
        lon: Double,
        targetEpochMs: Long
    ): AppResult<WeatherSnapshot> {
        return try {
            val response = RetrofitClient.weatherApi
                .getForecast(lat, lon, apiKey, "metric", "kr")
            val city = response.city?.name ?: ""
            val slot = pickSlot(response, targetEpochMs)
                ?: return AppResult.Error(
                    IllegalStateException("no forecast slot available"),
                    "예보 슬롯을 찾지 못했어요"
                )
            AppResult.Success(slot.toSnapshot(cityName = city))
        } catch (e: HttpException) {
            AppResult.NetworkError(e.code(), "예보 서버 오류 (${e.code()})")
        } catch (e: IOException) {
            AppResult.Error(e, "네트워크 연결을 확인해주세요")
        } catch (e: Exception) {
            AppResult.Error(e, "예보 정보를 불러올 수 없어요")
        }
    }

    /**
     * targetEpochMs 를 포함하는 3시간 슬롯을 찾는다.
     *
     * OWM forecast 의 각 entry.dt 는 해당 슬롯 **시작 UTC 초**. 한 슬롯은
     * `[dt*1000, (dt+10800)*1000)` 범위를 담당. 정렬된 list 를 순회하며
     * 조건 만족하는 첫 항목 반환. 못 찾으면 가장 가까운 entry 로 폴백.
     */
    private fun pickSlot(response: ForecastResponse, targetEpochMs: Long): ForecastEntry? {
        val list = response.list
        if (list.isEmpty()) return null

        // 정확 매칭: dt ≤ target < dt + 3h
        val exact = list.firstOrNull { entry ->
            val startMs = entry.dt * 1000L
            val endMs   = startMs + THREE_HOURS_MS
            targetEpochMs in startMs until endMs
        }
        if (exact != null) return exact

        // 폴백: target 이 list 범위보다 과거면 첫 번째, 미래면 마지막
        return list.minByOrNull { kotlin.math.abs(it.dt * 1000L - targetEpochMs) }
    }

    private fun WeatherResponse.toSnapshot(): WeatherSnapshot {
        val cond = weather.firstOrNull()
        val type = WeatherConditionType.fromCode(cond?.id ?: 800)
        return WeatherSnapshot(
            conditionType = type,
            description   = buildDescription(type),
            tempCelsius   = main.temp,
            cityName      = cityName,
            rainMmh       = rain?.mmh(),
            snowMmh       = snow?.mmh(),
            sources       = setOf(WeatherSource.OWM)
        )
    }

    private fun ForecastEntry.toSnapshot(cityName: String): WeatherSnapshot {
        val cond = weather.firstOrNull()
        val type = WeatherConditionType.fromCode(cond?.id ?: 800)
        return WeatherSnapshot(
            conditionType = type,
            description   = buildDescription(type),
            tempCelsius   = main.temp,
            cityName      = cityName,
            rainMmh       = rain?.mmh(),
            snowMmh       = snow?.mmh(),
            sources       = setOf(WeatherSource.OWM)
        )
    }

    private fun buildDescription(type: WeatherConditionType): String = when (type) {
        WeatherConditionType.RAIN  -> "비가 내리고 있어요 ☔"
        WeatherConditionType.SNOW  -> "눈이 내리고 있어요 ❄️"
        WeatherConditionType.CLEAR -> "비, 눈 감지 없음"
    }

    private companion object {
        const val THREE_HOURS_MS: Long = 3L * 60 * 60 * 1000
    }
}
