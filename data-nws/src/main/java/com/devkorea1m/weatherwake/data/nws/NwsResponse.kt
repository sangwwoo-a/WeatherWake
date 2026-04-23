package com.devkorea1m.weatherwake.data.nws

import com.google.gson.annotations.SerializedName

/**
 * NWS (api.weather.gov) 응답 DTO.
 *
 * 2-step 플로우:
 *  1) GET /points/{lat},{lon} → properties.observationStations URL
 *  2) GET {stationsUrl} → 첫 station.id → GET /stations/{id}/observations/latest
 *
 * 여기서는 단순화를 위해 /points 응답으로 얻은 관측소 리스트의 첫 station 을
 * 사용하도록 Provider 에서 처리. 날씨 자체는 station observation 의
 * properties.textDescription + rawMessage + precipitationLastHour 로 판단.
 */

/** GET /points/{lat},{lon} 응답 */
data class NwsPointsResponse(
    @SerializedName("properties") val properties: NwsPointsProps
)

data class NwsPointsProps(
    @SerializedName("observationStations") val observationStationsUrl: String,
    @SerializedName("forecastHourly")      val forecastHourlyUrl: String? = null,
    @SerializedName("relativeLocation")    val relativeLocation: NwsRelativeLocation? = null
)

data class NwsRelativeLocation(
    @SerializedName("properties") val properties: NwsRelativeLocationProps
)

data class NwsRelativeLocationProps(
    @SerializedName("city")  val city: String = "",
    @SerializedName("state") val state: String = ""
)

/** GET {observationStationsUrl} 응답 */
data class NwsStationsResponse(
    @SerializedName("features") val features: List<NwsStationFeature> = emptyList()
)

data class NwsStationFeature(
    @SerializedName("properties") val properties: NwsStationProps
)

data class NwsStationProps(
    @SerializedName("stationIdentifier") val stationIdentifier: String
)

/** GET /stations/{id}/observations/latest 응답 */
data class NwsObservationResponse(
    @SerializedName("properties") val properties: NwsObservationProps
)

data class NwsObservationProps(
    @SerializedName("textDescription")       val textDescription: String = "",
    @SerializedName("temperature")           val temperature: NwsUnitValue? = null,
    @SerializedName("precipitationLastHour") val precipitationLastHour: NwsUnitValue? = null
)

/**
 * NWS 의 단위 포함 값 포맷.
 * unitCode 예: "wmoUnit:mm" (강수), "wmoUnit:degC" (온도)
 * value 가 null 일 수 있음 (관측 결측).
 */
data class NwsUnitValue(
    @SerializedName("value")    val value: Double? = null,
    @SerializedName("unitCode") val unitCode: String = ""
)

/**
 * GET {forecastHourly} 응답 — 7일×1시간 간격 예보.
 *
 * 각 period 는 startTime/endTime (ISO-8601 + TZ offset), shortForecast
 * ("Light Rain", "Partly Cloudy" 등), temperature(F/C),
 * probabilityOfPrecipitation 을 제공. NWS forecastHourly 는
 * precipitationAmount 필드가 일관적이지 않아 text 기반 분류를 주 신호로 사용.
 */
data class NwsForecastResponse(
    @SerializedName("properties") val properties: NwsForecastProps
)

data class NwsForecastProps(
    @SerializedName("periods") val periods: List<NwsForecastPeriod> = emptyList()
)

data class NwsForecastPeriod(
    @SerializedName("startTime")                  val startTime: String,   // ISO-8601 w/ TZ
    @SerializedName("endTime")                    val endTime: String,
    @SerializedName("temperature")                val temperature: Double? = null,
    @SerializedName("temperatureUnit")            val temperatureUnit: String = "F",
    @SerializedName("probabilityOfPrecipitation") val probabilityOfPrecipitation: NwsUnitValue? = null,
    @SerializedName("shortForecast")              val shortForecast: String = ""
)
