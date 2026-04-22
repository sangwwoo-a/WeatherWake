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

    /** 위경도 → 격자(nx, ny) */
    fun toGrid(lat: Double, lon: Double): Grid {
        val degrad = PI / 180.0
        val re  = RE / GRID
        val slat1 = SLAT1 * degrad
        val slat2 = SLAT2 * degrad
        val olon  = OLON * degrad
        val olat  = OLAT * degrad

        var sn = ln(cos(slat1) / cos(slat2)) / ln(tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5))
        val sf = tan(PI * 0.25 + slat1 * 0.5).pow(sn) * cos(slat1) / sn
        val ro = re * sf / tan(PI * 0.25 + olat * 0.5).pow(sn)

        val ra = re * sf / tan(PI * 0.25 + lat * degrad * 0.5).pow(sn)
        var theta = lon * degrad - olon
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= sn

        val nx = floor(ra * sin(theta) + XO + 0.5).toInt()
        val ny = floor(-ra * cos(theta) + YO + 0.5).toInt()
        return Grid(nx, ny)
    }
}
