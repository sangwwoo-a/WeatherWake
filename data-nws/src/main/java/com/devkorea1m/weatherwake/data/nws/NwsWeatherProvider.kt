package com.devkorea1m.weatherwake.data.nws

import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.repository.AppResult
import com.devkorea1m.weatherwake.domain.WeatherProvider
import com.devkorea1m.weatherwake.domain.WeatherSnapshot
import retrofit2.HttpException
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * NWS (미국 기상청) 기반 [WeatherProvider].
 *
 * 요청 3회 필요 (points → stations → latest observation). 다소 비효율적이지만
 * 90분에 한 번 호출이라 문제없음. 나중에 /gridpoints/{office}/{x,y} 경로로
 * 바꾸면 2-step 으로 줄일 수 있음.
 *
 * conditionType 판정 우선순위:
 *  1) precipitationLastHour > 0 → RAIN (SNOW 구분은 textDescription 에 snow/flurr 포함 여부로)
 *  2) textDescription 에 rain/shower/drizzle → RAIN
 *  3) textDescription 에 snow/flurr/sleet → SNOW
 *  4) 나머지 → CLEAR
 */
class NwsWeatherProvider(
    userAgent: String,
    private val api: NwsApiService = NwsRetrofitClient(userAgent).api
) : WeatherProvider {

    override suspend fun getCurrentWeather(
        lat: Double,
        lon: Double
    ): AppResult<WeatherSnapshot> {
        return try {
            val point = api.getPoint("%.4f,%.4f".format(lat, lon))
            val cityLabel = point.properties.relativeLocation?.properties?.let {
                if (it.city.isNotBlank()) "${it.city}, ${it.state}" else ""
            } ?: ""

            val stations = api.getStations(point.properties.observationStationsUrl)
            val stationId = stations.features.firstOrNull()?.properties?.stationIdentifier
                ?: return AppResult.Error(IllegalStateException("no station"), "주변 관측소를 찾지 못했어요")

            val obs = api.getLatestObservation(stationId).properties

            // Defense-in-depth: 온도 측정값이 아예 없으면(관측소 오프라인·
            // 센서 결측) 0.0°C 로 폴백해서 표시하는 대신 Error 로 전환해
            // CrossValidatingWeatherProvider 가 OWM 으로 폴백하게 한다.
            // KMA 의 -999 sentinel 가드(v1.2.4) 와 대칭.
            if (obs.temperature?.value == null) {
                return AppResult.Error(
                    IllegalStateException("NWS observation has no temperature at ($lat,$lon)"),
                    "NWS 관측 데이터 결측"
                )
            }
            AppResult.Success(obs.toSnapshot(cityLabel))
        } catch (e: HttpException) {
            AppResult.NetworkError(e.code(), "NWS 서버 오류 (${e.code()})")
        } catch (e: IOException) {
            AppResult.Error(e, "네트워크 연결을 확인해주세요")
        } catch (e: Exception) {
            AppResult.Error(e, "NWS 정보를 불러올 수 없어요")
        }
    }

    private fun NwsObservationProps.toSnapshot(city: String): WeatherSnapshot {
        val text = textDescription.lowercase()
        val rainMmh = precipitationLastHour?.mmh()
        val hasRain = text.contains("rain") || text.contains("shower") || text.contains("drizzle")
        val hasSnow = text.contains("snow") || text.contains("flurr") || text.contains("sleet")

        val condition = when {
            hasSnow                        -> WeatherConditionType.SNOW
            hasRain                        -> WeatherConditionType.RAIN
            (rainMmh ?: 0f) > 0f           -> WeatherConditionType.RAIN   // 텍스트엔 없지만 강수 관측됨
            else                           -> WeatherConditionType.CLEAR
        }

        val tempC = temperature?.let { uv ->
            when {
                uv.value == null -> 0.0
                uv.unitCode.endsWith("degF") -> (uv.value - 32.0) * 5.0 / 9.0
                else -> uv.value   // degC 가정
            }
        } ?: 0.0

        return WeatherSnapshot(
            conditionType = condition,
            // 일부 공항 관측소(KEWR Newark Liberty 등) 는 textDescription 이 결측으로
            // 빈 문자열로 옴. 그대로 빈 값 유지해야 CrossValidatingWeatherProvider.merge
            // 의 isNotBlank() 가드가 secondary(OWM) description 또는 neutral 문구로
            // 폴백. 예전엔 "—" 로 치환했는데 그게 isNotBlank 통과해 UI 에 emdash 한
            // 글자만 뜨는 결과 발생 (Newark 사용자 제보).
            description   = textDescription,
            tempCelsius   = tempC,
            cityName      = city,
            rainMmh       = if (condition == WeatherConditionType.RAIN) rainMmh else null,
            snowMmh       = if (condition == WeatherConditionType.SNOW) rainMmh else null
        )
    }

    /** wmoUnit:mm → Float (1h 누적). null/0 이하는 null 로 반환해 "측정 없음" 표현 */
    private fun NwsUnitValue.mmh(): Float? {
        val v = value ?: return null
        return v.toFloat().takeIf { it > 0f }
    }

    // ─── 예보(forecastHourly) 경로 ─────────────────────────────────────

    override suspend fun getForecastAt(
        lat: Double,
        lon: Double,
        targetEpochMs: Long
    ): AppResult<WeatherSnapshot> {
        return try {
            val point = api.getPoint("%.4f,%.4f".format(lat, lon))
            val cityLabel = point.properties.relativeLocation?.properties?.let {
                if (it.city.isNotBlank()) "${it.city}, ${it.state}" else ""
            } ?: ""
            val forecastUrl = point.properties.forecastHourlyUrl
                ?: return AppResult.Error(
                    IllegalStateException("no forecastHourly URL"),
                    "해당 지역의 예보 URL을 받지 못했어요"
                )

            val forecast = api.getForecastHourly(forecastUrl)
            val period = pickPeriod(forecast.properties.periods, targetEpochMs)
                ?: return AppResult.Error(
                    IllegalStateException("no matching forecast period"),
                    "예보 슬롯을 찾지 못했어요"
                )

            // Defense-in-depth: period.temperature 가 null 이면 0.0°C 폴백
            // 대신 Error 로 전환해 aggregator 가 OWM 폴백하게 함. KMA -999
            // sentinel 가드와 대칭.
            if (period.temperature == null) {
                return AppResult.Error(
                    IllegalStateException("NWS forecast period has no temperature at ($lat,$lon)"),
                    "NWS 예보 데이터 결측"
                )
            }
            AppResult.Success(period.toSnapshot(cityLabel))
        } catch (e: HttpException) {
            AppResult.NetworkError(e.code(), "NWS 서버 오류 (${e.code()})")
        } catch (e: IOException) {
            AppResult.Error(e, "네트워크 연결을 확인해주세요")
        } catch (e: Exception) {
            AppResult.Error(e, "NWS 예보를 불러올 수 없어요")
        }
    }

    /**
     * periods 에서 targetEpochMs 가 속한 1시간 슬롯을 찾는다.
     * 정확 매칭 실패 시 가장 가까운 period 로 폴백.
     */
    private fun pickPeriod(
        periods: List<NwsForecastPeriod>,
        targetEpochMs: Long
    ): NwsForecastPeriod? {
        if (periods.isEmpty()) return null

        fun parseMs(iso: String): Long? = try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) { null } catch (_: Exception) { null }

        // 정확: startMs ≤ target < endMs
        periods.forEach { p ->
            val startMs = parseMs(p.startTime) ?: return@forEach
            val endMs   = parseMs(p.endTime)   ?: return@forEach
            if (targetEpochMs in startMs until endMs) return p
        }
        // 폴백: target 과 start 거리 최소
        return periods.minByOrNull {
            parseMs(it.startTime)?.let { ms -> kotlin.math.abs(ms - targetEpochMs) } ?: Long.MAX_VALUE
        }
    }

    private fun NwsForecastPeriod.toSnapshot(city: String): WeatherSnapshot {
        val text = shortForecast.lowercase()
        val hasSnow = text.contains("snow") || text.contains("flurr") || text.contains("sleet")
        val hasRain = text.contains("rain") || text.contains("shower") || text.contains("drizzle") ||
                       text.contains("thunder")
        val pop = probabilityOfPrecipitation?.value ?: 0.0   // 0~100 %

        // NWS 예보에 mm/h 실측은 없음. shortForecast + PoP 로 판정.
        // 보수 기조: hasSnow 가 text 에 있으면 SNOW, hasRain 이면 RAIN, 둘 다 없고
        // PoP >= 50% 면 text 상 "partly cloudy, slight chance" 같은 경계 케이스로
        // 간주해 RAIN (안전우선).
        val condition = when {
            hasSnow                -> WeatherConditionType.SNOW
            hasRain                -> WeatherConditionType.RAIN
            pop >= 50.0            -> WeatherConditionType.RAIN
            else                   -> WeatherConditionType.CLEAR
        }

        // NWS forecastHourly temperature 는 unit 필드로 F/C 구분
        val tempC = temperature?.let { t ->
            if (temperatureUnit.equals("F", ignoreCase = true))
                (t - 32.0) * 5.0 / 9.0
            else
                t
        } ?: 0.0

        return WeatherSnapshot(
            conditionType = condition,
            // shortForecast 가 비어있는 경우(드물지만 있음)엔 그대로 빈 문자열 → aggregator fallback
            description   = shortForecast,
            tempCelsius   = tempC,
            cityName      = city,
            // mm/h 실측 없음 → null 로 두면 Worker fallback 이 보통 민감도에서 트리거
            rainMmh       = null,
            snowMmh       = null
        )
    }
}
