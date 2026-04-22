package com.devkorea1m.weatherwake.data.repository

import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.model.WeatherResponse
import com.devkorea1m.weatherwake.data.network.RetrofitClient
import com.devkorea1m.weatherwake.domain.WeatherProvider
import com.devkorea1m.weatherwake.domain.WeatherSnapshot
import retrofit2.HttpException
import java.io.IOException

/**
 * OpenWeatherMap 기반 [WeatherProvider] 구현.
 *
 * API 키는 생성 시점에 주입받는다. 호출부는 [WeatherProvider] 인터페이스만
 * 알면 되므로 이후 KMA/NWS 공급자 또는 교차검증 아그리게이터로 쉽게 교체 가능.
 *
 * @return AppResult.Success — 정상
 *         AppResult.NetworkError — HTTP 오류 (4xx/5xx)
 *         AppResult.Error — 네트워크 미연결, 타임아웃 등
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

    private fun WeatherResponse.toSnapshot(): WeatherSnapshot {
        val cond = weather.firstOrNull()
        val type = WeatherConditionType.fromCode(cond?.id ?: 800)
        return WeatherSnapshot(
            conditionType = type,
            description   = buildDescription(type),
            tempCelsius   = main.temp,
            cityName      = cityName,
            rainMmh       = rain?.oneHour,
            snowMmh       = snow?.oneHour
        )
    }

    private fun buildDescription(type: WeatherConditionType): String = when (type) {
        WeatherConditionType.RAIN  -> "비가 내리고 있어요 ☔"
        WeatherConditionType.SNOW  -> "눈이 내리고 있어요 ❄️"
        WeatherConditionType.CLEAR -> "비, 눈 감지 없음"
    }
}
