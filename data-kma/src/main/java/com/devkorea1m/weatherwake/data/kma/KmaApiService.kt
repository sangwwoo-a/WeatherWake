package com.devkorea1m.weatherwake.data.kma

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 기상청 단기예보/실황 API (VilageFcstInfoService_2.0).
 *
 * - 인증키: 공공데이터포털에서 발급한 serviceKey (URL-encoded)
 * - numOfRows: 한 번에 가져올 카테고리 수. 초단기실황은 8~10개 카테고리.
 * - dataType: "JSON" 고정 (기본은 XML)
 *
 * 베이스 URL: http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/
 */
interface KmaApiService {

    /**
     * 초단기실황조회 — 현재 시각 기준 관측값 (매시각 40분에 해당 시각 자료 제공).
     *
     * @param baseDate yyyyMMdd (예: 20260422)
     * @param baseTime HHmm, 매시각 00분 기준 (예: "1300"). 호출 시점이 HH:40 이전이면
     *                 직전 시각(HH-1:00)을 넘길 것.
     */
    @GET("getUltraSrtNcst")
    suspend fun getUltraShortNowcast(
        @Query("serviceKey") serviceKey: String,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("numOfRows")  numOfRows: Int = 10,
        @Query("dataType")   dataType: String = "JSON",
        @Query("base_date")  baseDate: String,
        @Query("base_time")  baseTime: String,
        @Query("nx")         nx: Int,
        @Query("ny")         ny: Int
    ): KmaResponse
}
