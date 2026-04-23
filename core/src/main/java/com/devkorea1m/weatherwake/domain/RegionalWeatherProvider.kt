package com.devkorea1m.weatherwake.domain

import com.devkorea1m.weatherwake.data.repository.AppResult

/**
 * 좌표 기반으로 적절한 [WeatherProvider] 를 선택해 위임하는 라우터.
 *
 * - KR 좌표 → `CrossValidatingWeatherProvider(kmaProvider, owmProvider)` — 지역 특화 1차 + OWM 2차
 * - US 좌표 → `CrossValidatingWeatherProvider(nwsProvider, owmProvider)` — NWS 1차 + OWM 2차
 * - 그 외 좌표 → `owmProvider` 단독
 *
 * 각 호출마다 새 `CrossValidatingWeatherProvider` 인스턴스를 만드는 건 의도적:
 *  - aggregator 는 상태 없음(pure)이라 생성 비용이 무시할 수준
 *  - KR/US 가 동일 OS 인스턴스에서 오가는 여행 시나리오 (한국 개발자 → 미국 출장
 *    → 귀국) 를 투명하게 지원. 사용자가 앱을 껐다 켜지 않아도 다음 요청부터
 *    자동 전환.
 *
 * 앱은 이 provider 하나만 [com.devkorea1m.weatherwake.runtime.WeatherWakeRuntime] 에
 * 등록하면 되고, [com.devkorea1m.weatherwake.worker.WeatherCheckWorker] 와 UI 는
 * 지역 판정을 의식하지 않고 단일 인터페이스만 사용한다.
 *
 * 향후 AU/UK 등을 추가할 땐 생성자에 공급자 하나 더 받고 [Region] enum 에 케이스만
 * 늘리면 Worker/UI 무변경.
 */
class RegionalWeatherProvider(
    private val kmaProvider: WeatherProvider,
    private val nwsProvider: WeatherProvider,
    private val owmProvider: WeatherProvider,
) : WeatherProvider {

    override suspend fun getCurrentWeather(
        lat: Double,
        lon: Double
    ): AppResult<WeatherSnapshot> = providerFor(lat, lon).getCurrentWeather(lat, lon)

    override suspend fun getForecastAt(
        lat: Double,
        lon: Double,
        targetEpochMs: Long
    ): AppResult<WeatherSnapshot> = providerFor(lat, lon).getForecastAt(lat, lon, targetEpochMs)

    private fun providerFor(lat: Double, lon: Double): WeatherProvider =
        when (Region.fromCoordinates(lat, lon)) {
            Region.KR    -> CrossValidatingWeatherProvider(kmaProvider, owmProvider)
            Region.US    -> CrossValidatingWeatherProvider(nwsProvider, owmProvider)
            Region.OTHER -> owmProvider
        }
}
