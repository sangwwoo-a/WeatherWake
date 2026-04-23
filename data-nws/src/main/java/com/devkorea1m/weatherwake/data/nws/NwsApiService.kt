package com.devkorea1m.weatherwake.data.nws

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

/**
 * National Weather Service (미국) API.
 *
 * - API 키 불필요
 * - 모든 요청에 User-Agent 헤더 필수 (NwsRetrofitClient 가 주입)
 * - Base URL: https://api.weather.gov
 *
 * 2-step 구조:
 *  1) /points/{lat},{lon} → observationStations URL
 *  2) observationStations URL (절대경로, @Url 사용) → stations 목록 → 첫 station id
 *  3) /stations/{id}/observations/latest → 관측값
 */
interface NwsApiService {

    @GET("points/{latLon}")
    suspend fun getPoint(@Path("latLon") latLon: String): NwsPointsResponse

    /** /points 응답의 observationStations 가 절대 URL 이라 그대로 호출 */
    @GET
    suspend fun getStations(@Url url: String): NwsStationsResponse

    @GET("stations/{id}/observations/latest")
    suspend fun getLatestObservation(@Path("id") stationId: String): NwsObservationResponse

    /** /points 응답의 forecastHourly URL (절대경로) 호출 */
    @GET
    suspend fun getForecastHourly(@Url url: String): NwsForecastResponse
}
