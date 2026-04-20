# Changelog

이 문서는 WeatherWake의 버전별 변경 사항을 기록합니다.
형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 규칙을 따릅니다.

## [1.1.5] - 2026-04-20 (build 15)

### 🐛 Fixed

- **잠금화면 위 알람 미표시** — Android 15(One UI 7) 환경의 Background Activity Launch(BAL) 제한과 Samsung Edge Lighting이 full-screen intent 알림을 가로채던 문제 해결. 표준 Android 경로만으로도 잠금화면 위로 알람 화면이 안정적으로 표시됨.
- **비 앞당김 날 두 번 울리던 문제(B-1)** — 앞당겨진 알람 발동 후 AlarmReceiver가 다음 날 예약 시 `fromMs = 오늘 원래 시각 + 1분`으로 탐색하도록 수정. 오늘 원래 시각은 이미 소비된 것으로 간주되어 추가 울림 없음.
- **다음 날 날씨 Worker 미예약(B-2)** — `AlarmScheduler.schedule()`이 호출될 때마다 `WeatherCheckWorker.scheduleFor()`도 동기 호출하도록 변경. Receiver의 자동 재예약에서도 다음 날 90분 전 체크가 살아있음.
- **스누즈 시 알람음 유실(B-3)** — 스누즈로 재예약되는 알람이 항상 기본음으로 대체되던 버그 수정. 사용자가 고른 음원이 그대로 유지됨.
- **알림창 "알람 끄기" 무반응** — Android 13+ `RECEIVER_NOT_EXPORTED` 제약으로 브로드캐스트가 Activity에 도달 못하던 문제. `sendBroadcast(...).setPackage(packageName)` 로 수정.
- **Android 15 BAL로 MainActivity 복귀 실패** — `AlarmActionReceiver`에서 차단되던 `startActivity(MainActivity)` 제거. Activity 측 `finishReceiver`가 대신 처리.
- **서비스 재시작 후 1회성 알람 자동 비활성화 실패(#5)** — `DISMISS` 인텐트의 `EXTRA_ALARM_ID_ACTION` → DB 조회 순서로 fallback.

### ✨ Added

- **알람 저장 전 날씨 연동 작동 방식 안내 다이얼로그** — 알람음 선택 직후, "이해했어요"를 명시적으로 탭해야 DB에 저장됨. 표시 내용: 알람 시각 / 체크 시각(90분 전) / 비·눈 앞당김 분 / 체크 시점 이후 변화 미반영 경고. 사용자 인지 확인 + 법적 고지 역할 동시 수행.
- **배터리 최적화 예외 권한 온보딩** — 첫 실행 시 "권장" 수준으로 안내. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 권한으로 시스템 설정 바로 진입.
- **디버그 전용 날씨 시뮬레이션** — `local.properties`의 `weather.override=RAIN|SNOW|CLEAR`로 실제 날씨를 기다리지 않고 전체 파이프라인 검증. `BuildConfig.DEBUG` 이중 가드로 release 빌드에 절대 유출 안 됨.

### ♻ Changed

- **알람 경로 단순화** — 기존 "4중 메커니즘"(deprecated WakeLock + SYSTEM_ALERT_WINDOW 오버레이 + 직접 `startActivity` + fullScreenIntent)을 표준 Android 경로(fullScreenIntent)로 일원화. 경합·Play Store 리스크·OEM 호환성 문제 동시 해소.
- **배터리 최적화 안내 문구** — "반드시 필요합니다" → "권장합니다"로 완화. 실기기 검증 결과 `setAlarmClock`은 배터리 최적화와 무관하게 정시 발동됨을 확인.
- **Play Store 설명** — 사실 관계 보강. 구현되지 않은 "한파 감지" 문구 제거, "내일 예보 미리 체크" → "알람 90분 전 실시간 날씨 확인"으로 정정, "위치 서버 전송 안 함"(거짓) → "좌표만 OWM에 전송"으로 정확화.

### 🗑 Removed

- **`AlarmOverlayManager`** 클래스 및 `SYSTEM_ALERT_WINDOW` 권한 — Google Play 정책상 최소화 권고 대상이었고, 표준 fullScreenIntent 경로로 기능 대체 가능.
- **Notification `setSilent(true)` 플래그** — 일부 기기에서 fullScreenIntent 발동을 억제하는 원인이었음. 채널 자체가 무음 설정이라 중복.
- **`AlarmReceiver` 내 직접 `startActivity` 호출** — Android 15 BAL에 의해 "invisible launch" 처리되며 오히려 fullScreenIntent를 `"이미 Activity 실행 중"`으로 스킵시키는 부작용 발견.

### 🧪 Verified on-device

Galaxy S25+ (One UI 7 / Android 16, targetSdk 35)에서 다음 시나리오 실기기 검증 완료:

- 잠금화면 위 알람 표시 + 소리 + 진동
- "알람 끄기" → MainActivity 복귀
- "5분 더" → 5분 뒤 동일 알람음으로 재발동
- 날씨 RAIN 시뮬 → 30분 앞당김 + 앞당긴 알람 발동
- 앞당김 다음 날 → 원래 시각 + 날씨 Worker 정상 재예약
- 16:50 알람 + 60분 앞당김 시뮬 → 15:50 발동 및 동시 실행되는 Worker들 독립 처리

---

## [1.1.3] - 이전 버전

WeatherWake 공개 시점의 기본 기능 포함. 세부 이력은 이 버전부터 본 CHANGELOG에서 관리합니다.
