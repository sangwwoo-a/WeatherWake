package com.devkorea1m.weatherwake.data.kma

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

internal object KmaRetrofitClient {

    private const val BASE_URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            // 릴리즈에서도 헤더만 남기려면 HEADERS 로. 민감값(serviceKey)은 URL 에 들어가므로
            // 실 배포 시엔 NONE 권장.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val api: KmaApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(KmaApiService::class.java)
}
