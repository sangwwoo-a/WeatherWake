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

    /**
     * 초단기예보조회 — 호출 시점 + 6시간 범위, 1시간 간격.
     *
     * base_time 규칙: 매시각 30분 기준 (0030, 0130, ... 2330) 으로 발표되며
     * 발표 15 분 후(HH:45) 부터 가용. 호출부는 [KmaWeatherProvider] 가
     * 계산해 넘김.
     *
     * 응답 items 각각에 fcstDate/fcstTime/category/fcstValue 필드가 있어
     * 카테고리(PTY/RN1/T1H/SKY 등) × 예보시각 매트릭스를 돌려준다.
     * numOfRows 를 넉넉히 잡아 한 번에 전부 수신.
     */
    @GET("getUltraSrtFcst")
    suspend fun getUltraShortForecast(
        @Query("serviceKey") serviceKey: String,
        @Query("pageNo")     pageNo: Int = 1,
        @Query("numOfRows")  numOfRows: Int = 60,   // 6시간 × 10 카테고리 여유
        @Query("dataType")   dataType: String = "JSON",
        @Query("base_date")  baseDate: String,
        @Query("base_time")  baseTime: String,
        @Query("nx")         nx: Int,
        @Query("ny")         ny: Int
    ): KmaForecastResponse
}
