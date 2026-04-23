package com.devkorea1m.weatherwake.domain

import com.devkorea1m.weatherwake.data.repository.AppResult

/**
 * 날씨 공급자 추상화. OWM / KMA / NWS 등 구현이 이 인터페이스를 만족하면
 * WeatherCheckWorker 와 UI 가 제공자 종류에 무관하게 동일 로직으로 날씨를
 * 조회할 수 있다.
 *
 * 교차 검증용 아그리게이터·지역 라우터도 같은 인터페이스를 구현하면 된다.
 *
 * API 키·엔드포인트 등 provider별 설정은 구현체 생성자에서 받고, 호출 시점엔
 * 좌표(+타깃 시각)만 넘기도록 의도적으로 좁혔다.
 *
 * ### 두 조회 경로
 *
 * | 메서드 | 엔드포인트 유형 | 사용처 |
 * |---|---|---|
 * | [getCurrentWeather] | 실황 (nowcast, 현재 관측) | 메인 화면 상단 날씨 카드 — "지금 날씨" |
 * | [getForecastAt] | 단기 예보 (forecast, 지정 시각 예측) | WeatherCheckWorker — "알람 시각의 예보로 앞당김 판단" |
 *
 * 두 메서드는 같은 WeatherSnapshot 타입을 돌려주지만 내부 호출하는 API 는
 * 전혀 다르다 — KMA 는 getUltraSrtNcst vs getUltraSrtFcst, NWS 는
 * /observations/latest vs /forecast/hourly, OWM 은 /weather vs /forecast.
 *
 * ### 구 버전(v1.2.x)과의 관계
 *
 * 1.2 까지는 Worker 도 getCurrentWeather 를 썼지만, 알람 시각과 체크 시각(T-90분)
 * 사이 90 분 동안 날씨가 변하면 앞당김 결정이 실제 기상과 어긋나는 문제가 있었음.
 * 1.3 부턴 Worker 가 getForecastAt(alarmMs) 로 전환해 "알람 시각의 예보"를 쓴다.
 * UI 상단 카드는 기존처럼 실황 유지 — 사용자가 앱을 지금 열었으니 지금 날씨를 보는 게
 * 자연스러움.
 */
interface WeatherProvider {

    /**
     * 현재 실황 (nowcast). 지금 이 순간의 관측값.
     *
     * @return AppResult.Success — 정상
     *         AppResult.NetworkError — HTTP 오류 (4xx/5xx 또는 공급자 자체 오류코드)
     *         AppResult.Error — 네트워크 미연결, 타임아웃 등
     */
    suspend fun getCurrentWeather(lat: Double, lon: Double): AppResult<WeatherSnapshot>

    /**
     * 지정한 시각([targetEpochMs]) 의 단기 예보.
     *
     * 반환 스냅샷은 **해당 시각을 포함하는 예보 슬롯** 의 값. 공급자별 해상도:
     *  - KMA 초단기예보: 1 시간 간격
     *  - NWS forecastHourly: 1 시간 간격
     *  - OWM 5-day/3-hour forecast: 3 시간 간격
     *
     * 슬롯 선택 규칙: `slot.startMs ≤ targetEpochMs < slot.endMs` 인 슬롯.
     * 범위 밖(너무 먼 미래 등)이면 가장 가까운 마지막/처음 슬롯으로 폴백.
     *
     * @param targetEpochMs 예보를 보고 싶은 시각 (ms since epoch, UTC). 일반적으로
     *                      WeatherCheckWorker 가 `alarmMs` 를 넘김.
     */
    suspend fun getForecastAt(
        lat: Double,
        lon: Double,
        targetEpochMs: Long
    ): AppResult<WeatherSnapshot>
}
