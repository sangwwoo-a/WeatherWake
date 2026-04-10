package com.devkorea1m.weatherwake.data.model

import com.google.gson.annotations.SerializedName

/** OpenWeatherMap Current Weather API 응답 모델 */
data class WeatherResponse(
    @SerializedName("weather") val weather: List<WeatherCondition>,
    @SerializedName("main") val main: MainInfo,
    @SerializedName("name") val cityName: String,
    @SerializedName("dt") val timestamp: Long
)

data class WeatherCondition(
    @SerializedName("id") val id: Int,          // 날씨 코드 (2xx=뇌우, 3xx=이슬비, 5xx=비, 6xx=눈...)
    @SerializedName("main") val main: String,   // "Rain", "Snow", "Clear" 등
    @SerializedName("description") val description: String,
    @SerializedName("icon") val icon: String
)

data class MainInfo(
    @SerializedName("temp") val temp: Double,
    @SerializedName("feels_like") val feelsLike: Double,
    @SerializedName("humidity") val humidity: Int
)
