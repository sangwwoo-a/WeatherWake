package com.devkorea1m.weatherwake.domain

/**
 * 이 스냅샷의 **실제** 데이터 제공자 식별자.
 *
 * Region([com.devkorea1m.weatherwake.domain.Region])은 "어느 공급자를 시도할까"
 * 라는 **라우팅 의도**를 나타내지만, 실제로 값을 준 공급자와 다를 수 있다.
 * 예) 사용자가 Monterrey(US 박스 안이지만 멕시코 도시)에 있으면 NWS 가
 * 404 를 반환하고 [com.devkorea1m.weatherwake.domain.CrossValidatingWeatherProvider]
 * 가 OWM 으로 폴백한다. 이 경우 snapshot.sources = {OWM} 이 되어야 UI 가
 * 정직하게 "OpenWeatherMap 제공" 이라고 표시할 수 있다.
 *
 * Attribution UI 의 진실의 원천(single source of truth). Region 은 라우팅에만
 * 사용하고, 사용자에게 보이는 출처 표기는 항상 [WeatherSnapshot.sources] 로부터.
 */
enum class WeatherSource {
    KMA,   // 기상청 국가기후데이터센터
    NWS,   // US National Weather Service
    OWM    // OpenWeatherMap
}
