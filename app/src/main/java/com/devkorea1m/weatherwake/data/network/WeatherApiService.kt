package com.devkorea1m.weatherwake.data.network

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
}
