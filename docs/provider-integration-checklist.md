# 공급자 추가 체크리스트

새 기상 공급자(BOM, JMA, MetService 등)를 `WeatherProvider` 로 통합할 때
반드시 따라야 하는 절차와 원칙.

> **맥락**: 2026-04-24 Oita(-999℃) 사건 이후 정립. 라우팅 오판정·공급자 in-band
> sentinel·attribution 거짓 표기가 한 번에 터진 사례를 통해 배운 방어 패턴을
> 체크리스트화. 자세한 사건 경위는 CHANGELOG 1.2.4 / 1.2.5 참조.

---

## 원칙 (깨지 않을 것)

### 1. 공급자 어댑터의 책임: **원본 에러 표현을 AppResult.Error 로 번역**
- HTTP 200 이라도 payload 에 sentinel(-999, null, 빈 배열) 이 있으면 `AppResult.Error`
- "성공 응답 = 값 신뢰 가능" 이어야 함. 그 외는 모두 실패로 간주.
- 이 번역이 없으면 `CrossValidatingWeatherProvider` 의 OWM 폴백이 우회됨.

### 2. Attribution 의 진실의 원천: **WeatherSnapshot.sources**
- Region 은 "시도할 공급자" (라우팅 힌트) 일 뿐. 실제 UI 에 표시되는 출처는
  반드시 `weather.sources` 기반.
- 공급자가 Success 를 반환할 때 자기 `WeatherSource` 라벨을 반드시 포함.
- `CrossValidatingWeatherProvider.merge()` 는 성공한 공급자 합집합 전파.

### 3. bbox 는 근사치일 뿐 — 경계 케이스는 항상 터진다
- 국경 쌍둥이 도시(El Paso/Juárez 등) 는 bbox 로 분리 불가.
- 정확한 국경 판정은 reverse-geocoding 이 필요. v2 로 연기.
- bbox 경계 오류는 원칙 1·2 가 자동으로 커버 (사용자에게는 OWM 으로 정확한 값 + 정직한 attribution).

---

## 공급자별 상태

### ✅ 안전하게 추가 가능
- **KMA** (한국 기상청) — 이미 통합. 한반도 전역 커버.
- **NWS** (미 국립기상청) — 이미 통합. 미국 전역 커버.
- **OWM** (OpenWeatherMap) — 이미 통합. 전 세계 폴백.

### 🟢 다음 추가 후보 (안전)
- **BOM** (Australia Bureau of Meteorology)
  - 지리적 격리: 이웃국과 바다로 수백 km 이상 분리 (NZ 1800km, Indonesia 500km+)
  - 제안 박스: `lat in -44.0..-10.5 && lon in 113.0..154.0 -> AU`
  - Kupang(인니 -10.17°S) / Port Moresby(PNG -9.45°S) 자동 제외 (북쪽 -10.5 컷)
  - Auckland(NZ 174.76°E) / Vanuatu(168°E) / Fiji(178°E) 자동 제외 (경도 154 컷)
  - 외딴 호주령(Christmas · Cocos · Norfolk · Lord Howe, 합산 ~5,000명) 은 v1 엔 OTHER 로 폴백 허용.

### 🔴 현재 방식으론 **절대 추가 금지** (v2 reverse-geocode 선행 필요)
- **JMA** (일본 기상청)
  - 근거: 한일 경계 bbox 분리 근본 불가 + **독도 주권 이슈**
  - 치명 시나리오: 독도(37.24°N, 131.87°E) → bbox 상 KR/JP 어느 박스든 겹쳐 JMA 가 호출될 수 있음 → "한국 사용자에게 독도 날씨를 일본 기상청에서 받아왔다고 표시" → 리뷰 재난 + 언론 이슈
  - 실패하는 좌표 쌍:
    | 지점 | 주권 | 좌표 | bbox 분리 가능? |
    |---|---|---|---|
    | 부산 🇰🇷 | KR | 35.18, 129.07 | ❌ 쓰시마 34.3, 129.3 와 14km |
    | 쓰시마 🇯🇵 | JP | 34.3, 129.3 | 〃 |
    | 울릉도 🇰🇷 | KR | 37.5, 130.9 | ❌ 오키제도 36.2, 133.3 와 근접 |
    | 독도 🇰🇷 | KR | 37.24, 131.87 | ❌ JP 연안과 동일 경도대 |
  - **대안 경로**:
    1. **권장**: `app-jp` 별도 모듈로 JMA 전용 일본 앱 출시 (한국 앱과 공급자 분리)
    2. v2 reverse-geocode 도입 후 country code 기반 라우팅 (독도 = KR 명시)
    3. "주권 민감 지역 하드코딩" — 독도·울릉도 반경 원형 우선 KR 고정

### 🟡 기타 검토 필요
- **MetService NZ**: 호주와 비슷한 지리적 격리. bbox 안전.
- **DWD (독일)**: 유럽은 국가 수 많고 국경 복잡. v2 reverse-geocode 후 검토.
- **Met Office (UK)**: 섬나라라 bbox 가능하나 아일랜드 공화국과 섬 공유.

---

## 공급자 추가 체크리스트

1. [ ] **WeatherSource enum 에 항목 추가** (`core/.../domain/WeatherSource.kt`)
2. [ ] **Region enum 에 항목 추가** + bbox 정의 (`core/.../domain/Region.kt`)
   - 이웃국 주요 도시 좌표 eyeball 검증
   - 특히 국경·해상 경계·외딴 영토 케이스 나열
3. [ ] **Provider 구현**
   - API 의 "no data" 표현 방식 문서화 (HTTP 404? 빈 배열? sentinel?)
   - 어댑터에서 모든 "no data" 표현을 `AppResult.Error` 로 번역
   - Success 반환 시 `sources = setOf(WeatherSource.XXX)` 포함
   - `getCurrentWeather` / `getForecastAt` 양쪽 동일 정책 적용
4. [ ] **RegionalWeatherProvider 라우팅 추가**
5. [ ] **Attribution 문자열 추가** (ko/en/default 3 locale)
   - `weather_attribution_xx` (2-공급자 문구)
   - `weather_attribution_xx_single` (단독 폴백 문구 — 필요 시)
   - `MainActivity.renderAttribution()` 의 when 분기에 케이스 추가
6. [ ] **디버그 좌표 주입기에 경계 케이스 도시 추가**
   - 정상 케이스 + 이웃국 주요 도시 + bbox 경계 도시
7. [ ] **해외 테스터 확보**
   - 해당 공급자 지역에 거주하는 테스터 1명 이상
   - 출시 전 Internal Testing 트랙 필수
8. [ ] **CHANGELOG 업데이트 + versionCode bump**

---

## 유지해야 할 테스트 좌표 (회귀 방지)

디버그 좌표 주입기(`MainActivity.showDebugCoordInjector`) 에 다음을 유지:

| 카테고리 | 도시 | 좌표 | 기대 sources |
|---|---|---|---|
| KR 정상 | Seoul | 37.57, 126.98 | {KMA, OWM} |
| KR 경계(울릉·독도 박스) | Dokdo | 37.24, 131.87 | {KMA, OWM} |
| US 정상 | San Francisco | 37.77, -122.42 | {NWS, OWM} |
| US 판핸들 | Juneau | 58.3, -134.42 | {NWS, OWM} |
| 큐슈 (PR#25 수정) | Oita | 33.24, 131.61 | {OWM} |
| 캐나다 (PR#25 수정) | Vancouver | 49.28, -123.12 | {OWM} |
| 유콘 (PR#25 수정) | Whitehorse | 60.72, -135.05 | {OWM} |
| 멕시코 경계 | Monterrey | 25.67, -100.32 | {OWM} |
| bbox 한계 | Toronto | 43.65, -79.38 | {OWM} (NWS 404 폴백) |
| bbox 한계 | Tijuana | 32.51, -117.04 | {OWM} (NWS 404 폴백) |
| OTHER 단독 | Hong Kong | 22.32, 114.17 | {OWM} |

새 공급자 추가 시 **이 목록은 절대 줄어들지 않아야** 한다 (회귀 방지).
새 공급자 커버리지에 해당하는 도시들을 추가만 할 것.

---

## 원칙 재확인

> **알람과 날씨는 신뢰도가 생명. 신뢰가 담보되지 못 하면 이 프로젝트는 안 하는 게 낫다.**

- 공급자 추가로 기능이 늘어나는 것보다, **잘못된 출처 표기로 신뢰가 깨지는 것**
  의 비용이 훨씬 크다.
- 의심스러운 케이스는 추가 보류. OWM 폴백으로 기능은 멀쩡하다.
- 특히 주권·영토 민감 지역에서는 bbox 보다 reverse-geocode 가 **최소 요구사양**.
