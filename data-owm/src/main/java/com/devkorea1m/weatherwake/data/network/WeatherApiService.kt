package com.devkorea1m.weatherwake.data.network

import com.devkorea1m.weatherwake.data.model.ForecastResponse
import com.devkorea1m.weatherwake.data.model.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {

    /**
     * OpenWeatherMap Current Weather API
     * Kotlin interface 기본값은 Retrofit 프록시에서 동작하지 않으므로
     * units / lang은 호출부에서 명시적으로 전달한다.
     */
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("lat")   lat: Double,
        @Query("lon")   lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String,
        @Query("lang")  lang: String
    ): WeatherResponse

    /**
     * OpenWeatherMap 5-day/3-hour Forecast API — 무료 tier 지원.
     * 3시간 간격, 최대 5일(총 40슬롯) 까지 반환. 주소 지정 목적엔
     * targetEpochMs 를 포함하는 슬롯만 골라 쓴다.
     *
     * cnt 로 필요한 앞부분만 제한 가능하지만 90분 후 예보는 보통 첫 1~2 슬롯
     * 안에 들어와 기본값(미지정)이어도 응답 크기 문제 없음.
     */
    @GET("forecast")
    suspend fun getForecast(
        @Query("lat")   lat: Double,
        @Query("lon")   lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String,
        @Query("lang")  lang: String
    ): ForecastResponse
}
