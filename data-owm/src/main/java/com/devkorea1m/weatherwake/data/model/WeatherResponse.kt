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

/**
 * 강수/적설량 정보.
 *  - Current Weather API (/weather) 는 `"1h"` 필드 (지난 1시간, mm)
 *  - 5-day/3-hour Forecast API (/forecast) 는 `"3h"` 필드 (해당 3시간 슬롯, mm)
 *
 * [mmh] 편의 접근자로 호출부가 엔드포인트 종류 신경 안 쓰고 mm/h 로 환산된 값 사용.
 */
data class PrecipInfo(
    @SerializedName("1h") val oneHour: Float = 0f,
    @SerializedName("3h") val threeHour: Float = 0f
) {
    /** "1h" 가 있으면 그대로, 없으면 "3h"/3 로 mm/h 환산. 둘 다 0 이면 null */
    fun mmh(): Float? = when {
        oneHour > 0f   -> oneHour
        threeHour > 0f -> threeHour / 3f
        else           -> null
    }
}
