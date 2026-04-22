package com.devkorea1m.weatherwake.util

import android.content.Context
import com.devkorea1m.weatherwake.core.R

object AlarmConstants {
    /** 비트마스크: 일~토 모두 체크 = 매일 반복 */
    const val REPEAT_EVERY_DAY = 0b1111111

    /** 알람 울리기 몇 분 전에 날씨를 체크할지 */
    const val WEATHER_CHECK_BEFORE_MIN = 90L

    /** 기본 앞당김 분: 비 */
    const val DEFAULT_RAIN_ADVANCE_MIN = 30

    /** 기본 앞당김 분: 눈 */
    const val DEFAULT_SNOW_ADVANCE_MIN = 60

    /** 앞당김 선택지 (분) */
    val ADVANCE_OPTIONS = listOf(0, 15, 30, 45, 60)

    /** WorkManager 재시도 간격 (분) */
    const val WORKER_BACKOFF_MIN = 5L

    /** 앞당김 선택지 레이블 (긴 형식 — 설정 화면 스피너용) */
    fun getAdvanceLabelsFull(context: Context) = listOf(
        context.getString(R.string.advance_none),
        context.getString(R.string.advance_15min),
        context.getString(R.string.advance_30min),
        context.getString(R.string.advance_45min),
        context.getString(R.string.advance_one_hour)
    )
}
