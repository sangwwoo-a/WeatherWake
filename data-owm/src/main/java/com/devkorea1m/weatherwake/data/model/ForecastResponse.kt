package com.devkorea1m.weatherwake.data.model

import com.google.gson.annotations.SerializedName

/**
 * OpenWeatherMap 5-day/3-hour Forecast API 응답.
 *
 * `/data/2.5/forecast` — 무료 tier 에서 제공. 현재부터 5일까지 3시간 간격.
 * 각 `list[i]` 가 하나의 예보 슬롯(`dt` 는 슬롯 시작 UTC 초).
 *
 * WeatherRepository.getForecastAt 은 targetEpochMs 를 포함하는 슬롯을
 * (`dt * 1000 ≤ targetMs < (dt + 3h) * 1000`) 선형 탐색으로 찾아 해당
 * 슬롯 하나만 WeatherSnapshot 으로 변환.
 */
data class ForecastResponse(
    @SerializedName("list") val list: List<ForecastEntry> = emptyList(),
    @SerializedName("city") val city: ForecastCity? = null
)

data class ForecastEntry(
    @SerializedName("dt")      val dt: Long,                    // UTC epoch seconds (슬롯 시작)
    @SerializedName("weather") val weather: List<WeatherCondition>,
    @SerializedName("main")    val main: MainInfo,
    @SerializedName("rain")    val rain: PrecipInfo? = null,    // OWM forecast 에선 "3h" 필드이지만 @SerializedName("3h") 포함돼 동작
    @SerializedName("snow")    val snow: PrecipInfo? = null
)

data class ForecastCity(
    @SerializedName("name") val name: String = ""
)
