package com.devkorea1m.weatherwake.data.model

/**
 * 날씨 코드 → 앞당김 트리거 분류
 *
 * OpenWeatherMap 날씨 코드 범위:
 * 2xx = 뇌우(Thunderstorm) → 비와 동일하게 처리
 * 3xx = 이슬비(Drizzle)    → RAIN
 * 5xx = 비(Rain)           → RAIN
 * 6xx = 눈(Snow)           → SNOW
 * 7xx = 대기 현상 (안개 등) → CLEAR (앞당김 없음)
 * 800 = 맑음               → CLEAR
 * 8xx = 구름               → CLEAR
 */
enum class WeatherConditionType {
    RAIN,   // 비, 이슬비, 뇌우 → rainAdvanceMin 적용
    SNOW,   // 눈              → snowAdvanceMin 적용
    CLEAR;  // 맑음/구름/기타  → 앞당김 없음

    companion object {
        fun fromCode(code: Int): WeatherConditionType = when (code) {
            in 200..299 -> RAIN   // 뇌우 → 비와 동일 처리
            in 300..399 -> RAIN   // 이슬비
            in 500..599 -> RAIN   // 비
            in 600..699 -> SNOW   // 눈
            else        -> CLEAR
        }
    }
}
