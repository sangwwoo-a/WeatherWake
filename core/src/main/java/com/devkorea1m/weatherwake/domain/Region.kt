package com.devkorea1m.weatherwake.domain

/**
 * 좌표 기반 지역 판정.
 *
 * WeatherWake 는 지역별로 다른 1차 공급자(KMA / NWS / …) + OWM 2차 조합으로
 * 교차 검증한다. 지역 판정이 공급자 라우팅의 열쇠.
 *
 * 구현 방식은 의도적으로 **단순 바운딩 박스**:
 *  - ISO 역지오코딩은 네트워크 왕복 + API 의존 + 실패 가능성. 여기서는 "어느
 *    공급자 쓸까?" 라는 가벼운 분기 용도라 수 밀리초 내 결정이 중요.
 *  - 바운딩 박스는 하와이·알래스카·제주 같은 비인접 영토만 개별 박스로 추가.
 *  - 괌·푸에르토리코·영연방 해외 영토 등 애매한 케이스는 v1 에서 OTHER 로
 *    떨어져 OWM 단독 조회. 사용자가 대부분의 여행 시나리오에서 불편함 없음.
 *  - 더 정확한 판정이 필요해지면 나중에 reverse-geocode 기반으로 교체 가능.
 *
 * 박스 좌표는 약간 여유(margin) 를 두어 해안·국경 근접 지점도 포함되도록 함.
 */
enum class Region {
    /** 대한민국 (한반도 + 제주도. 독도 포함) */
    KR,

    /** 미국 (본토 + 알래스카 + 하와이) */
    US,

    /** 그 외 — OWM 단독 경로 */
    OTHER;

    companion object {

        fun fromCoordinates(lat: Double, lon: Double): Region = when {
            // 한반도 본토 + 제주. 동쪽 경계를 129.7°E 로 끊어 일본 큐슈
            // (Fukuoka 130.4°E / Oita 131.6°E / Nagasaki 129.9°E) 를 명확히
            // 제외한다. 이전 124.0°~132.0°E 박스는 큐슈 서부를 KR 로 오판정해
            // KMA 호출 → sentinel(-999℃) 표시 버그를 일으켰음 (v1.2.2 테스터
            // 제보, Oita 좌표). 쓰시마(34.3°N, 129.3°E) 는 이 박스 안에 남지만
            // 실제 사용자 수가 극히 적어 v1 에서는 수용. 더 정밀한 경계가
            // 필요하면 추후 reverse-geocode 기반으로 교체.
            lat in 33.0..38.7 && lon in 124.5..129.7 -> KR

            // 울릉도(37.5°N, 130.9°E) · 독도(37.24°N, 131.87°E) — 위도를
            // 37°~37.7° 로 제한해 큐슈(북위 33°대) 와 완전히 분리.
            lat in 37.0..37.7 && lon in 130.5..132.0 -> KR

            // 미국 본토. 북쪽 경계 49°N 은 캐나다(BC·앨버타·서스캐처원·
            // 매니토바·온타리오 서부) 국경과 일치해 Vancouver(49.28°N)·
            // Calgary(51°N) 등 서부 캐나다 도시 제외. 남쪽 경계 25°N 로
            // Monterrey(25.67°N) 같은 멕시코 북부 도시 제외.
            //
            // 한계(수용 가능한 cosmetic 버그): Toronto(43.65°N)·Montreal
            // (45.5°N)·Tijuana(32.5°N) 등은 박스 안에 남음. bbox 만으로는
            // 5대호 주변 미·캐 경계와 미·멕 경계를 깨끗하게 분리 불가.
            // NWS 가 해당 좌표에 404 로 응답 → CrossValidatingWeatherProvider
            // 가 OWM 단독으로 폴백 → 데이터는 정상 표시됨. 다만 attribution
            // 텍스트가 잠시 "NWS + OpenWeatherMap" 로 보이는 cosmetic 문제
            // 남음. 완전한 해결은 v2 reverse-geocode 로.
            lat in 25.0..49.0 && lon in -125.0..-66.0 -> US

            // 알래스카 본토: 54°~72°N, 141°~169°W. 동쪽 경계 141°W 는
            // AK/YT(Yukon) 국경선. Whitehorse(60.72°N, 135°W) 같은 Yukon
            // 도시가 이전 -130°W 경계에선 알래스카로 오판정됐음.
            lat in 54.0..72.0 && lon in -169.0..-141.0 -> US

            // 알래스카 판핸들 (Juneau 58.3°N, Sitka 57°N, Ketchikan 55.3°N):
            // 54°~60°N, 130°~141°W. 위도 60° 컷으로 Whitehorse(60.72°N) 등
            // Yukon 남부 도시와 분리.
            lat in 54.0..60.0 && lon in -141.0..-130.0 -> US

            // 하와이: 18°~23°N, 154°~161°W
            lat in 18.0..23.0 && lon in -161.0..-154.0 -> US

            else -> OTHER
        }
    }
}
