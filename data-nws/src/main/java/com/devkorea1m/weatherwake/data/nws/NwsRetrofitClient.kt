package com.devkorea1m.weatherwake.data.nws

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * NWS 는 User-Agent 헤더가 필수이며 식별 가능한 형식이어야 한다
 * (https://www.weather.gov/documentation/services-web-api 참조).
 * 권장 형식: "앱이름 연락처이메일" — 구현체가 생성자에서 주입.
 */
internal class NwsRetrofitClient(userAgent: String) {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept", "application/ld+json")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val api: NwsApiService = Retrofit.Builder()
        .baseUrl("https://api.weather.gov/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NwsApiService::class.java)
}
