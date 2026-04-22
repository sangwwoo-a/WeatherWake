package com.devkorea1m.weatherwake.domain

import com.devkorea1m.weatherwake.data.repository.AppResult

/**
 * 날씨 공급자 추상화. OWM / KMA / NWS 등 구현이 이 인터페이스를 만족하면
 * WeatherCheckWorker가 제공자 종류에 무관하게 동일 로직으로 비/눈 트리거를
 * 판단할 수 있다.
 *
 * 교차 검증용 아그리게이터(KR/US 전용)도 같은 인터페이스를 구현하면 된다:
 *   class CrossValidatingProvider(primary, secondary, weights) : WeatherProvider
 *
 * API 키·엔드포인트 등 provider별 설정은 구현체 생성자에서 받고,
 * 호출 시점엔 좌표만 넘기도록 의도적으로 좁혔다.
 */
interface WeatherProvider {
    suspend fun getCurrentWeather(lat: Double, lon: Double): AppResult<WeatherSnapshot>
}
