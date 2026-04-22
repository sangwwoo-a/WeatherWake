package com.devkorea1m.weatherwake.domain

import com.devkorea1m.weatherwake.data.model.WeatherConditionType

/**
 * Provider-독립 날씨 스냅샷.
 *
 * 강수량/적설량은 mm/h 실측값을 [rainMmh]/[snowMmh]에 직접 담는다.
 * null 은 "측정값 없음"을 의미하며 이 경우 소비자는 [conditionType]과
 * 민감도 임계값만으로 트리거 여부를 판단해야 한다 (API가 1h 필드를
 * 생략하는 이슬비/가벼운 눈 상황).
 */
data class WeatherSnapshot(
    val conditionType: WeatherConditionType,
    val description: String,
    val tempCelsius: Double,
    val cityName: String,
    val rainMmh: Float? = null,
    val snowMmh: Float? = null
)
