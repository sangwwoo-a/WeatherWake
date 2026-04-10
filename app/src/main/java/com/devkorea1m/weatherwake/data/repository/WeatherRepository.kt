package com.devkorea1m.weatherwake.data.repository

import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.model.WeatherResponse
import com.devkorea1m.weatherwake.data.network.RetrofitClient

data class WeatherResult(
    val conditionType: WeatherConditionType,
    val description: String,
    val tempCelsius: Double,
    val cityName: String
)

class WeatherRepository {

    /**
     * 현재 실시간 날씨 조회
     * @return WeatherResult, 네트워크 오류 시 null
     */
    suspend fun getCurrentWeather(
        lat: Double,
        lon: Double,
        apiKey: String
    ): WeatherResult? = runCatching {
        val response = RetrofitClient.weatherApi.getCurrentWeather(lat, lon, apiKey, "metric", "kr")
        response.toResult()
    }.getOrNull()

    private fun WeatherResponse.toResult(): WeatherResult {
        val cond = weather.firstOrNull()
        val type = WeatherConditionType.fromCode(cond?.id ?: 800)
        val desc = buildDescription(type, cond?.description ?: "")
        return WeatherResult(
            conditionType = type,
            description = desc,
            tempCelsius = main.temp,
            cityName = cityName
        )
    }

    private fun buildDescription(type: WeatherConditionType, raw: String): String = when (type) {
        WeatherConditionType.RAIN  -> "비가 내리고 있어요 ☔"
        WeatherConditionType.SNOW  -> "눈이 내리고 있어요 ❄️"
        WeatherConditionType.CLEAR -> "날씨가 괜찮아요 ☀️"
    }
}
