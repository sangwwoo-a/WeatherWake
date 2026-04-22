package com.devkorea1m.weatherwake.data.nws

import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.repository.AppResult
import com.devkorea1m.weatherwake.domain.WeatherProvider
import com.devkorea1m.weatherwake.domain.WeatherSnapshot
import retrofit2.HttpException
import java.io.IOException

/**
 * NWS (미국 기상청) 기반 [WeatherProvider].
 *
 * 요청 3회 필요 (points → stations → latest observation). 다소 비효율적이지만
 * 90분에 한 번 호출이라 문제없음. 나중에 /gridpoints/{office}/{x,y} 경로로
 * 바꾸면 2-step 으로 줄일 수 있음.
 *
 * conditionType 판정 우선순위:
 *  1) precipitationLastHour > 0 → RAIN (SNOW 구분은 textDescription 에 snow/flurr 포함 여부로)
 *  2) textDescription 에 rain/shower/drizzle → RAIN
 *  3) textDescription 에 snow/flurr/sleet → SNOW
 *  4) 나머지 → CLEAR
 */
class NwsWeatherProvider(
    userAgent: String,
    private val api: NwsApiService = NwsRetrofitClient(userAgent).api
) : WeatherProvider {

    override suspend fun getCurrentWeather(
        lat: Double,
        lon: Double
    ): AppResult<WeatherSnapshot> {
        return try {
            val point = api.getPoint("%.4f,%.4f".format(lat, lon))
            val cityLabel = point.properties.relativeLocation?.properties?.let {
                if (it.city.isNotBlank()) "${it.city}, ${it.state}" else ""
            } ?: ""

            val stations = api.getStations(point.properties.observationStationsUrl)
            val stationId = stations.features.firstOrNull()?.properties?.stationIdentifier
                ?: return AppResult.Error(IllegalStateException("no station"), "주변 관측소를 찾지 못했어요")

            val obs = api.getLatestObservation(stationId).properties
            AppResult.Success(obs.toSnapshot(cityLabel))
        } catch (e: HttpException) {
            AppResult.NetworkError(e.code(), "NWS 서버 오류 (${e.code()})")
        } catch (e: IOException) {
            AppResult.Error(e, "네트워크 연결을 확인해주세요")
        } catch (e: Exception) {
            AppResult.Error(e, "NWS 정보를 불러올 수 없어요")
        }
    }

    private fun NwsObservationProps.toSnapshot(city: String): WeatherSnapshot {
        val text = textDescription.lowercase()
        val rainMmh = precipitationLastHour?.mmh()
        val hasRain = text.contains("rain") || text.contains("shower") || text.contains("drizzle")
        val hasSnow = text.contains("snow") || text.contains("flurr") || text.contains("sleet")

        val condition = when {
            hasSnow                        -> WeatherConditionType.SNOW
            hasRain                        -> WeatherConditionType.RAIN
            (rainMmh ?: 0f) > 0f           -> WeatherConditionType.RAIN   // 텍스트엔 없지만 강수 관측됨
            else                           -> WeatherConditionType.CLEAR
        }

        val tempC = temperature?.let { uv ->
            when {
                uv.value == null -> 0.0
                uv.unitCode.endsWith("degF") -> (uv.value - 32.0) * 5.0 / 9.0
                else -> uv.value   // degC 가정
            }
        } ?: 0.0

        return WeatherSnapshot(
            conditionType = condition,
            description   = "${textDescription.ifBlank { "—" }} (NWS)",
            tempCelsius   = tempC,
            cityName      = city,
            rainMmh       = if (condition == WeatherConditionType.RAIN) rainMmh else null,
            snowMmh       = if (condition == WeatherConditionType.SNOW) rainMmh else null
        )
    }

    /** wmoUnit:mm → Float (1h 누적). null/0 이하는 null 로 반환해 "측정 없음" 표현 */
    private fun NwsUnitValue.mmh(): Float? {
        val v = value ?: return null
        return v.toFloat().takeIf { it > 0f }
    }
}
