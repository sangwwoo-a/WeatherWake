package com.devkorea1m.weatherwake.data.kma

import kotlin.math.*

/**
 * WGS84 위경도 ↔ 기상청 격자(nx, ny) 변환.
 *
 * 기상청은 Lambert Conformal Conic 투영을 사용하며, 투영 파라미터는 고정값이다.
 * 본 구현은 기상청 공식 "격자(LCC) ↔ 위경도 변환" 문서의 수식을 그대로 옮긴 것으로,
 * 전국 어느 지점이든 동일한 nx,ny를 얻을 수 있다.
 */
object KmaGridConverter {

    // LCC 투영 파라미터 (기상청 고정)
    private const val RE    = 6371.00877   // 지구 반경(km)
    private const val GRID  = 5.0           // 격자 간격(km)
    private const val SLAT1 = 30.0          // 표준 위도 1
    private const val SLAT2 = 60.0          // 표준 위도 2
    private const val OLON  = 126.0         // 기준점 경도
    private const val OLAT  = 38.0          // 기준점 위도
    private const val XO    = 43.0          // 기준점 X 좌표(격자)
    private const val YO    = 136.0         // 기준점 Y 좌표(격자)

    data class Grid(val nx: Int, val ny: Int)

    /** 기상청 유효 격자 범위 — 5km 격자 기준 한반도 전역 커버 */
    private const val NX_MIN = 1
    private const val NX_MAX = 149
    private const val NY_MIN = 1
    private const val NY_MAX = 253

    /**
     * 위경도 → 격자(nx, ny). 한국 영역 밖 좌표는 **null 반환**.
     *
     * LCC 역변환 자체는 지구상 어느 점에서도 유한한 정수 쌍을 내놓기 때문에,
     * 해외 좌표(미국·영국·괌 등) 를 넣어도 괄호 쳐진 "격자번호" 가 돌아온다.
     * 그 값을 KMA API 에 그대로 넘기면 resultCode=03(NODATA) 가 와서
     * WorkManager 가 [Result.retry]로 무한 backoff 재시도를 돌린다 → **배터리
     * 드레인**. 따라서 여기서 유효 범위 밖이면 null 을 돌려 caller 가 상위
     * fallback(예: OWM 단독) 경로로 깨끗하게 빠지게 한다.
     *
     * 참고: v1.8 의 [com.devkorea1m.weatherwake.domain.RegionalWeatherProvider]
     * 는 KR 좌표에서만 KmaWeatherProvider 를 호출하므로 이 null 경로는 실무상
     * 거의 타지 않는다. 하지만 (a) 좌표 경계 근처(33°N 이하, 132°E 이상 등)
     * 에서 Region.KR 로 분류됐지만 KMA 격자는 벗어나는 케이스, (b) 누군가
     * KmaWeatherProvider 를 직접 사용하는 미래 변경 에 대비한 defense-in-depth.
     */
    fun toGrid(lat: Double, lon: Double): Grid? {
        val degrad = PI / 180.0
        val re  = RE / GRID
        val slat1 = SLAT1 * degrad
        val slat2 = SLAT2 * degrad
        val olon  = OLON * degrad
        val olat  = OLAT * degrad

        val sn = ln(cos(slat1) / cos(slat2)) / ln(tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5))
        val sf = tan(PI * 0.25 + slat1 * 0.5).pow(sn) * cos(slat1) / sn
        val ro = re * sf / tan(PI * 0.25 + olat * 0.5).pow(sn)

        val ra = re * sf / tan(PI * 0.25 + lat * degrad * 0.5).pow(sn)
        var theta = lon * degrad - olon
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= sn

        val nx = floor(ra * sin(theta) + XO + 0.5).toInt()
        // ⚠️ 반드시 `ro - ra*cos(theta) + YO`. ro 를 빼먹으면 대한민국 전역에서
        // ny 가 -1200대 음수로 나와 KMA API 가 "파라미터 오류(resultCode=10)" 로 거절한다.
        val ny = floor(ro - ra * cos(theta) + YO + 0.5).toInt()

        if (nx !in NX_MIN..NX_MAX || ny !in NY_MIN..NY_MAX) return null
        return Grid(nx, ny)
    }
}
