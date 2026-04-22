package com.devkorea1m.weatherwake.domain

import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.repository.AppResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

/**
 * 두 개의 [WeatherProvider] 결과를 가중치 기반으로 합산해 단일 [WeatherSnapshot] 으로
 * 반환하는 안전우선(safety-first) 아그리게이터.
 *
 * 사용 의도:
 *  - KR 앱: `CrossValidatingWeatherProvider(OwmWeatherProvider, KmaWeatherProvider)`
 *  - US 앱: `CrossValidatingWeatherProvider(OwmWeatherProvider, NwsWeatherProvider)`
 *
 * 이렇게 감싸고 나면 [WeatherWakeRuntime.weatherProvider] 에 넣기만 하면 끝 — 기존
 * [com.devkorea1m.weatherwake.worker.WeatherCheckWorker] 코드를 한 줄도 바꾸지 않고도
 * 교차 검증이 활성화된다.
 *
 * ### 알고리즘
 *
 * 두 공급자를 `async` 로 동시에 호출. 각 결과에 대해:
 *  - `RAIN` 투표 → 그 공급자의 가중치만큼 `rainScore` 누적
 *  - `SNOW` 투표 → `snowScore` 누적
 *
 * `max(rainScore, snowScore) >= threshold` 면 precipitation 으로 판정. 동점이면
 * 안전우선 정책에 따라 `RAIN` 을 우선 (비는 우산으로 대응 가능, 눈보다 변동 빠름).
 *
 * **안전우선 정책이란?**
 * threshold=0.3, 가중치=(0.6, 0.4) 기본값에서는 *어느 한쪽이라도* 비/눈을 감지하면
 * 무조건 트리거된다. 즉 "놓치느니 한 번 더 깨우는 쪽". 더 보수적으로 "두 공급자 모두
 * 동의해야만 트리거" 로 쓰려면 threshold 를 0.8 이상으로 올리면 된다
 * (합산 1.0 이어야만 통과).
 *
 * ### 폴백 (부분 실패)
 *
 * 한쪽이 실패(`NetworkError`/`Error`)면 성공한 쪽의 스냅샷을 그대로 반환 — 사용자의
 * 알람 신뢰도를 네트워크 상태 때문에 일시적으로 낮추지 않는다. 둘 다 실패면 primary 의
 * 에러를 반환 (secondary 는 보조 신호로 간주).
 *
 * ### 파라미터 합산
 *
 * - `rainMmh` / `snowMmh`: 양쪽 모두 측정값이 있으면 max (안전우선). 한쪽만 있으면 그 값.
 * - `tempCelsius`: primary 값 우선 (안전 비관련, 가중치 1차 공급자 신뢰)
 * - `cityName`: primary 의 비공백 값 우선
 */
class CrossValidatingWeatherProvider(
    private val primary: WeatherProvider,
    private val secondary: WeatherProvider,
    private val weightPrimary: Float = DEFAULT_WEIGHT_PRIMARY,
    private val weightSecondary: Float = DEFAULT_WEIGHT_SECONDARY,
    private val threshold: Float = DEFAULT_THRESHOLD
) : WeatherProvider {

    init {
        require(weightPrimary in 0f..1f)   { "weightPrimary out of range: $weightPrimary" }
        require(weightSecondary in 0f..1f) { "weightSecondary out of range: $weightSecondary" }
        require(threshold > 0f && threshold <= (weightPrimary + weightSecondary)) {
            "threshold must be in (0, w1+w2]: $threshold vs ${weightPrimary + weightSecondary}"
        }
    }

    override suspend fun getCurrentWeather(
        lat: Double,
        lon: Double
    ): AppResult<WeatherSnapshot> = coroutineScope {
        // 각 공급자 호출을 [PROVIDER_TIMEOUT_MS] 내로 제한. 한 쪽이 hang 해도
        // (TCP 소켓 유실·DNS 지연·상대 서버 무응답 등) 나머지 공급자 결과로
        // 빠르게 폴백해 Worker 가 10분 ceiling 까지 버티다 failure 로 끝나는
        // "알람 앞당김 영구 실패" 시나리오 차단.
        //
        // `withTimeoutOrNull` 은 타임아웃 시 내부 코루틴을 cancel 한 뒤 null 반환
        // → 아래에서 null 을 "이 공급자는 타임아웃" 으로 구분해 처리.
        val a = async { withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { primary.getCurrentWeather(lat, lon) } }
        val b = async { withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { secondary.getCurrentWeather(lat, lon) } }
        val rp: AppResult<WeatherSnapshot>? = a.await()
        val rs: AppResult<WeatherSnapshot>? = b.await()

        val primarySnap   = (rp as? AppResult.Success)?.data
        val secondarySnap = (rs as? AppResult.Success)?.data

        when {
            primarySnap != null && secondarySnap != null ->
                AppResult.Success(merge(primarySnap, secondarySnap))

            // 한쪽만 성공 → 폴백. 사용자 알람 신뢰도 유지가 최우선.
            // (타임아웃된 공급자도 여기서는 "실패" 로 취급되어 fallback 대상이 됨)
            primarySnap   != null -> AppResult.Success(primarySnap)
            secondarySnap != null -> AppResult.Success(secondarySnap)

            // 둘 다 실패 또는 타임아웃 → 가장 정보량 많은 에러 전파.
            // primary 의 실제 에러가 있으면 그걸, 없으면(=타임아웃이면) secondary 의 것을,
            // 둘 다 타임아웃이면 명시적 TimeoutError 로 Worker 에게 retry 유도.
            else -> rp ?: rs ?: AppResult.Error(
                RuntimeException("both providers timed out"),
                "날씨 조회가 시간 초과되었어요"
            )
        }
    }

    private fun merge(p: WeatherSnapshot, s: WeatherSnapshot): WeatherSnapshot {
        val rainScore = (if (p.conditionType == WeatherConditionType.RAIN) weightPrimary else 0f) +
                        (if (s.conditionType == WeatherConditionType.RAIN) weightSecondary else 0f)
        val snowScore = (if (p.conditionType == WeatherConditionType.SNOW) weightPrimary else 0f) +
                        (if (s.conditionType == WeatherConditionType.SNOW) weightSecondary else 0f)

        val finalType = when {
            max(rainScore, snowScore) < threshold     -> WeatherConditionType.CLEAR
            rainScore >= snowScore                     -> WeatherConditionType.RAIN  // 동점시 RAIN 우선(안전우선)
            else                                       -> WeatherConditionType.SNOW
        }

        val rainMmh = if (finalType == WeatherConditionType.RAIN) maxOfNullable(p.rainMmh, s.rainMmh) else null
        val snowMmh = if (finalType == WeatherConditionType.SNOW) maxOfNullable(p.snowMmh, s.snowMmh) else null

        return WeatherSnapshot(
            conditionType = finalType,
            description   = buildDescription(p, s, finalType, rainScore, snowScore),
            tempCelsius   = p.tempCelsius,
            cityName      = p.cityName.ifBlank { s.cityName },
            rainMmh       = rainMmh,
            snowMmh       = snowMmh
        )
    }

    private fun buildDescription(
        p: WeatherSnapshot,
        s: WeatherSnapshot,
        finalType: WeatherConditionType,
        rainScore: Float,
        snowScore: Float
    ): String {
        val agree = p.conditionType == s.conditionType

        // primary(KMA 등 지역 특화) 가 같은 판정을 냈다면 그 description 을 그대로 살려
        // 사용자가 1차 공급자의 목소리를 UI 에서 직접 확인할 수 있게 한다. 판정이
        // 달라진 경우(안전우선 정책으로 finalType 이 바뀐 경우) 에만 aggregator 가
        // 생성한 중립 문구를 쓴다.
        val base = if (p.conditionType == finalType && p.description.isNotBlank()) {
            p.description
        } else {
            when (finalType) {
                WeatherConditionType.RAIN  -> "비가 내리고 있어요 ☔"
                WeatherConditionType.SNOW  -> "눈이 내리고 있어요 ❄️"
                WeatherConditionType.CLEAR -> "비, 눈 감지 없음"
            }
        }

        // 공급자 간 합의가 이루어졌으면 깨끗하게 base 만 표시. 불일치로 safety-first
        // 가 발동한 경우에만 사용자가 왜 앞당김이 일어났는지 이해할 수 있도록
        // 짧은 사유 문구를 덧붙인다. 디버그 점수([0.x/0.3]) 는 로그에서만 의미가
        // 있으므로 UI 문구에서는 생략.
        return if (agree) base else "$base · 안전우선 반영"
    }

    private fun maxOfNullable(a: Float?, b: Float?): Float? = when {
        a == null && b == null -> null
        a == null              -> b
        b == null              -> a
        else                   -> maxOf(a, b)
    }

    companion object {
        /** 1차 공급자 기본 가중치. 지역 특화 공급자(KMA/NWS)를 primary 로 두는 걸 가정 */
        const val DEFAULT_WEIGHT_PRIMARY: Float = 0.6f
        /** 2차 공급자 기본 가중치 (OWM 을 secondary 로 두는 걸 가정) */
        const val DEFAULT_WEIGHT_SECONDARY: Float = 0.4f

        /**
         * 각 공급자 호출당 최대 대기 시간 (ms).
         *
         * 기준: 실측상 NWS 3-step + OWM 병렬이 cold 상태에서도 1.5s 안에 끝남.
         * 12s 는 4G/LTE 나쁜 조건(느린 DNS, TCP 재시도 1회) 까지 커버하는
         * 안전 마진. 이 값을 넘으면 해당 공급자는 실패 간주하고 나머지 하나의
         * 결과로 폴백. Worker 의 WorkManager 10분 ceiling 과 비교해 여전히
         * 충분한 여유.
         *
         * 더 올리면: 네트워크 최악 조건에서 성공률 ↑, 앱 반응성 ↓.
         * 더 내리면: 반응성 ↑, 4G 변두리 지역 사용자에서 false-timeout 가능성 ↑.
         */
        const val PROVIDER_TIMEOUT_MS: Long = 12_000L
        /**
         * 기본 임계값. 0.3 은 "단일 공급자 투표만으로도 트리거" — 안전우선.
         * 양측 일치 강제는 0.8 이상으로 올릴 것.
         */
        const val DEFAULT_THRESHOLD: Float = 0.3f
    }
}
