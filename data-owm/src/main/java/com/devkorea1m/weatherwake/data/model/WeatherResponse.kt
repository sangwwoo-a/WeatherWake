package com.devkorea1m.weatherwake.data.model

import com.google.gson.annotations.SerializedName

/** OpenWeatherMap Current Weather API 응답 모델 */
data class WeatherResponse(
    @SerializedName("weather") val weather: List<WeatherCondition>,
    @SerializedName("main")    val main: MainInfo,
    @SerializedName("name")    val cityName: String,
    @SerializedName("dt")      val timestamp: Long,
    @SerializedName("rain")    val rain: PrecipInfo? = null,
    @SerializedName("snow")    val snow: PrecipInfo? = null
)

data class WeatherCondition(
    @SerializedName("id")          val id: Int,          // 날씨 코드 (2xx=뇌우, 3xx=이슬비, 5xx=비, 6xx=눈...)
    @SerializedName("main")        val main: String,     // "Rain", "Snow", "Clear" 등
    @SerializedName("description") val description: String,
    @SerializedName("icon")        val icon: String
)

data class MainInfo(
    @SerializedName("temp")       val temp: Double,
    @SerializedName("feels_like") val feelsLike: Double,
    @SerializedName("humidity")   val humidity: Int
)

/** 강수/적설량 정보 (OWM API: rain.1h / snow.1h, 단위 mm/h) */
data class PrecipInfo(
    @SerializedName("1h") val oneHour: Float = 0f   // 지난 1시간 강수량 (mm/h)
)
