package com.devkorea1m.weatherwake.data.kma

import com.google.gson.annotations.SerializedName

/**
 * 기상청 초단기실황조회 응답.
 *
 * items[*].category 주요 값:
 *  - PTY: 강수형태 (0=없음, 1=비, 2=비/눈, 3=눈, 5=빗방울, 6=빗방울눈날림, 7=눈날림)
 *  - RN1: 1시간 강수량 (mm) — 문자열. 강수 없음은 "강수없음" 또는 "0" 으로 옴
 *  - T1H: 기온 (섭씨)
 *
 * 실황은 nx,ny 격자 단위이므로 좌표는 KmaGridConverter.toGrid() 로 사전 변환.
 */
data class KmaResponse(
    @SerializedName("response") val response: KmaResponseBody
)

data class KmaResponseBody(
    @SerializedName("header") val header: KmaHeader,
    @SerializedName("body")   val body: KmaBody?
)

data class KmaHeader(
    @SerializedName("resultCode") val resultCode: String,
    @SerializedName("resultMsg")  val resultMsg: String
)

data class KmaBody(
    @SerializedName("items") val items: KmaItems?
)

data class KmaItems(
    @SerializedName("item") val item: List<KmaItem> = emptyList()
)

data class KmaItem(
    @SerializedName("baseDate") val baseDate: String,
    @SerializedName("baseTime") val baseTime: String,
    @SerializedName("category") val category: String,
    @SerializedName("nx")       val nx: Int,
    @SerializedName("ny")       val ny: Int,
    @SerializedName("obsrValue") val obsrValue: String
)
