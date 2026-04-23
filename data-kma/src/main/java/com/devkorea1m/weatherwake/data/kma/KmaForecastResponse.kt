package com.devkorea1m.weatherwake.data.kma

import com.google.gson.annotations.SerializedName

/**
 * 기상청 초단기예보(getUltraSrtFcst) 응답.
 *
 * 실황(getUltraSrtNcst)과 구조는 같지만 item 필드가 다르다:
 *  - 실황: baseDate/baseTime/category/obsrValue/nx/ny
 *  - 예보: baseDate/baseTime/category/fcstDate/fcstTime/fcstValue/nx/ny
 *
 * fcstDate(yyyyMMdd)/fcstTime(HHmm) 이 예보 대상 시각이며, 호출 시점에서 +0~6시간
 * 범위 내 1시간 간격으로 여러 item 이 내려옴. KmaWeatherProvider 는 target
 * epochMs 를 forecastHour(yyyyMMddHH) 로 환산해 해당 시각 item 들만 필터링.
 */
data class KmaForecastResponse(
    @SerializedName("response") val response: KmaForecastBody
)

data class KmaForecastBody(
    @SerializedName("header") val header: KmaHeader,
    @SerializedName("body")   val body: KmaForecastInnerBody?
)

data class KmaForecastInnerBody(
    @SerializedName("items") val items: KmaForecastItems?
)

data class KmaForecastItems(
    @SerializedName("item") val item: List<KmaForecastItem> = emptyList()
)

data class KmaForecastItem(
    @SerializedName("baseDate")  val baseDate: String,
    @SerializedName("baseTime")  val baseTime: String,
    @SerializedName("category")  val category: String,
    @SerializedName("fcstDate")  val fcstDate: String,
    @SerializedName("fcstTime")  val fcstTime: String,
    @SerializedName("fcstValue") val fcstValue: String,
    @SerializedName("nx")        val nx: Int,
    @SerializedName("ny")        val ny: Int
)
