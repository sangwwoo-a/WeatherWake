package com.devkorea1m.weatherwake.data.repository

import com.devkorea1m.weatherwake.data.model.PrecipInfo
import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.model.WeatherResponse
import com.devkorea1m.weatherwake.data.network.RetrofitClient
import retrofit2.HttpException
import java.io.IOException

data class WeatherResult(
    val conditionType: WeatherConditionType,
    val description: String,
    val tempCelsius: Double,
    val cityName: String,
    val rain: PrecipInfo? = null,
    val snow: PrecipInfo? = null
)

class WeatherRepository {

    /**
     * 현재 실시간 날씨 조회.
     *
     * @return AppResult.Success — 정상
     *         AppResult.NetworkError — HTTP 오류 (4xx/5xx)
     *         AppResult.Error — 네트워크 미연결, 타임아웃 등
     */
    suspend fun getCurrentWeather(
        lat: Double,
        lon: Double,
        apiKey: String
    ): AppResult<WeatherResult> {
        return try {
            val response = RetrofitClient.weatherApi
                .getCurrentWeather(lat, lon, apiKey, "metric", "kr")
            AppResult.Success(response.toResult())
        } catch (e: HttpException) {
            AppResult.NetworkError(e.code(), "날씨 서버 오류 (${e.code()})")
        } catch (e: IOException) {
            AppResult.Error(e, "네트워크 연결을 확인해주세요")
        } catch (e: Exception) {
            AppResult.Error(e, "날씨 정보를 불러올 수 없어요")
        }
    }

    private fun WeatherResponse.toResult(): WeatherResult {
        val cond = weather.firstOrNull()
        val type = WeatherConditionType.fromCode(cond?.id ?: 800)
        return WeatherResult(
            conditionType = type,
            description   = buildDescription(type),
            tempCelsius   = main.temp,
            cityName      = cityName,
            rain          = rain,
            snow          = snow
        )
    }

    private fun buildDescription(type: WeatherConditionType): String = when (type) {
        WeatherConditionType.RAIN  -> "비가 내리고 있어요 ☔"
        WeatherConditionType.SNOW  -> "눈이 내리고 있어요 ❄️"
        WeatherConditionType.CLEAR -> "비, 눈 감지 없음"
    }
}
