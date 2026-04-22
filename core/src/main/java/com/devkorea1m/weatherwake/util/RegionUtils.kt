package com.devkorea1m.weatherwake.util

import android.content.Context
import com.devkorea1m.weatherwake.domain.Region

/**
 * 저장된 위치 좌표 기준으로 현재 [Region] 반환.
 *
 * 저장된 위치가 없으면 기본값 [Region.KR] (1차 출시 시장). 동기 호출 — GPS 는 건드리지
 * 않으므로 UI 에서 지역 적응 문구를 즉시 보여주고 싶을 때 사용. (GPS 를 의무로 쓰면
 * 최초 실행 시 매번 권한 대화상자가 띄워져 UX 저하)
 *
 * 사용처: AlarmSettingActivity 의 "이 알람에 설정된 내용" 다이얼로그,
 *  WeatherRain/SnowActivity 하단 공급자 안내 문구.
 */
fun currentRegion(context: Context): Region {
    val loc = LocationHelper.getSavedLocation(context) ?: return Region.KR
    return Region.fromCoordinates(loc.lat, loc.lon)
}
