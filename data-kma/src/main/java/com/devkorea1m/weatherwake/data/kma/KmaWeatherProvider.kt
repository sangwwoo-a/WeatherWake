package com.devkorea1m.weatherwake.data.kma

import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.repository.AppResult
import com.devkorea1m.weatherwake.domain.WeatherProvider
import com.devkorea1m.weatherwake.domain.WeatherSnapshot
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 기상청 초단기실황 기반 [WeatherProvider].
 *
 * 초단기실황은 매시각 40분 즈음 해당 시각의 자료가 제공되므로, 호출 시점이
 * HH:40 이전이면 한 시간 앞선 base_time 으로 조회한다. (자정 직후엔 전일 2300)
 *
 * PTY(강수형태) 코드:
 *   0=없음, 1=비, 2=비/눈, 3=눈, 5=빗방울, 6=빗방울눈날림, 7=눈날림
 * RN1 은 "강수없음" 문자열로 올 수 있음 → null 처리.
 *
 * 좌표 변환: [KmaGridConverter.toGrid] (Lambert Conformal Conic).
 */
class KmaWeatherProvider(
    private val serviceKey: String,
    private val api: KmaApiService = KmaRetrofitClient.api
) : WeatherProvider {

    override suspend fun getCurrentWeather(
        lat: Double,
        lon: Double
    ): AppResult<WeatherSnapshot> {
        return try {
            val grid = KmaGridConverter.toGrid(lat, lon)
                ?: return AppResult.Error(
                    IllegalArgumentException("coords ($lat,$lon) outside KMA grid bounds"),
                    "이 좌표는 기상청 격자 범위 밖입니다"
                )
            val (baseDate, baseTime) = resolveBaseDateTime()

            val response = api.getUltraShortNowcast(
                serviceKey = serviceKey,
                baseDate   = baseDate,
                baseTime   = baseTime,
                nx         = grid.nx,
                ny         = grid.ny
            )

            val header = response.response.header
            if (header.resultCode != "00") {
                return AppResult.NetworkError(
                    code    = header.resultCode.toIntOrNull() ?: -1,
                    message = "기상청 오류: ${header.resultMsg}"
                )
            }
            val items = response.response.body?.items?.item.orEmpty()
            AppResult.Success(items.toSnapshot())
        } catch (e: HttpException) {
            AppResult.NetworkError(e.code(), "기상청 서버 오류 (${e.code()})")
        } catch (e: IOException) {
            AppResult.Error(e, "네트워크 연결을 확인해주세요")
        } catch (e: Exception) {
            AppResult.Error(e, "기상청 정보를 불러올 수 없어요")
        }
    }

    private fun List<KmaItem>.toSnapshot(): WeatherSnapshot {
        val byCategory = associateBy { it.category }
        val pty  = byCategory["PTY"]?.obsrValue?.toIntOrNull() ?: 0
        val rn1  = byCategory["RN1"]?.obsrValue?.parseMmh()
        val t1h  = byCategory["T1H"]?.obsrValue?.toDoubleOrNull() ?: 0.0

        val condition = when (pty) {
            1, 5        -> WeatherConditionType.RAIN            // 비, 빗방울
            2, 6        -> WeatherConditionType.RAIN            // 비/눈, 빗방울눈날림 → 안전우선 비로 분류
            3, 7        -> WeatherConditionType.SNOW            // 눈, 눈날림
            else        -> WeatherConditionType.CLEAR
        }

        // 혼합 강수(PTY 2/6) 는 mm/h 를 의도적으로 null 로 설정한다.
        //   이유: RN1 은 액체 환산량이라 wet snow 에서 통근 영향 대비 작게
        //   보고됨(예: 실제 함박눈 상황에서 0.2~0.5 mm/h). 그대로 rainMmh 에
        //   넣으면 "보통(임계 1.0 mm/h)" 민감도 사용자에서 임계값 비교가
        //   실패해 앞당김이 발동하지 않는 버그가 있었음.
        //   null 로 두면 Worker 가 conditionType 코드만으로 fallback 판정을
        //   수행(민감도 "보통" 포함 ≤ 2 에서 트리거). 임계 비교를 우회해
        //   "기상청이 혼합 강수라고 단언하면 일단 깨운다"는 safety-first
        //   철학과 일치.
        //   순수 비(1/5) · 순수 눈(3/7) 은 기존대로 mm/h 보고 — 임계값 비교가
        //   의미 있기 때문.
        val isMixed = pty == 2 || pty == 6

        val rainMmhOut = when {
            condition != WeatherConditionType.RAIN -> null
            isMixed                                -> null   // fallback 경로 유도
            else                                   -> rn1
        }
        val snowMmhOut = when {
            condition != WeatherConditionType.SNOW -> null
            else                                   -> rn1
        }

        return WeatherSnapshot(
            conditionType = condition,
            description   = kmaDescription(pty),
            tempCelsius   = t1h,
            cityName      = "",                                 // KMA는 도시명 제공 안 함
            rainMmh       = rainMmhOut,
            snowMmh       = snowMmhOut
        )
    }

    /** "강수없음" / "1mm 미만" / "30.0" 등 다양하게 옴 → Float? 로 변환 */
    private fun String.parseMmh(): Float? {
        if (isBlank() || this == "강수없음") return null
        if (contains("미만")) return 0.1f   // "1mm 미만" 은 소량 강수로 취급
        return trim().removeSuffix("mm").toFloatOrNull()
    }

    private fun kmaDescription(pty: Int): String = when (pty) {
        1 -> "비가 내리고 있어요 ☔"
        2 -> "비/눈이 내리고 있어요 🌨"
        3 -> "눈이 내리고 있어요 ❄️"
        5 -> "빗방울이 떨어지고 있어요 💧"
        6 -> "빗방울·눈날림 🌨"
        7 -> "눈날림 🌨"
        else -> "비, 눈 감지 없음"
    }

    /** 호출 시점 기준 유효한 base_date/base_time 산출. 기준: 매시 40분 이후 해당시 자료 */
    private fun resolveBaseDateTime(now: Calendar = Calendar.getInstance(KST)): Pair<String, String> {
        val cal = (now.clone() as Calendar).apply {
            // HH:40 이전이면 1시간 이전 자료 사용
            if (get(Calendar.MINUTE) < 40) add(Calendar.HOUR_OF_DAY, -1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return DATE_FMT.format(cal.time) to TIME_FMT.format(cal.time)
    }

    private companion object {
        val KST: TimeZone = TimeZone.getTimeZone("Asia/Seoul")
        val DATE_FMT = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = KST }
        val TIME_FMT = SimpleDateFormat("HHmm", Locale.US).apply { timeZone = KST }
    }
}
