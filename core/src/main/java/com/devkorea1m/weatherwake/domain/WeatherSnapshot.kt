package com.devkorea1m.weatherwake.domain

import com.devkorea1m.weatherwake.data.model.WeatherConditionType

/**
 * Provider-독립 날씨 스냅샷.
 *
 * 강수량/적설량은 mm/h 실측값을 [rainMmh]/[snowMmh]에 직접 담는다.
 * null 은 "측정값 없음"을 의미하며 이 경우 소비자는 [conditionType]과
 * 민감도 임계값만으로 트리거 여부를 판단해야 한다 (API가 1h 필드를
 * 생략하는 이슬비/가벼운 눈 상황).
 *
 * [sources] 는 이 스냅샷을 **실제로 구성한** 공급자 집합. UI 의 attribution
 * 은 반드시 이 필드를 읽어 결정해야 하며, Region 기반 라우팅 가정에
 * 의존해선 안 된다 (Region 은 "시도할 공급자" 이고 여기 값은 "성공한 공급자" —
 * 둘이 다를 수 있음). 예: Monterrey(US 박스 안)에서 NWS 가 404 → OWM 폴백
 * 시 sources = {OWM} 이 되어야 attribution 이 "OpenWeatherMap 제공" 으로 표시.
 */
data class WeatherSnapshot(
    val conditionType: WeatherConditionType,
    val description: String,
    val tempCelsius: Double,
    val cityName: String,
    val rainMmh: Float? = null,
    val snowMmh: Float? = null,
    val sources: Set<WeatherSource> = emptySet()
)
