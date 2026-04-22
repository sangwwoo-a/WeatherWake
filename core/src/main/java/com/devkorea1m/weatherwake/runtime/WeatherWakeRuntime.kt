package com.devkorea1m.weatherwake.runtime

import com.devkorea1m.weatherwake.domain.WeatherProvider

/**
 * 앱-레벨 의존성 레지스트리.
 *
 * 각 앱(app-kr / app-us)이 `Application.onCreate()` 진입점에서 [configure] 를
 * 호출해 자기 스택에 맞는 AlarmReceiver / MainActivity / WeatherProvider 를
 * 등록한다. 이후 :core 의 `AlarmScheduler` · `WeatherCheckWorker` 는 이 레지스트리를
 * 통해 해당 타입을 참조하므로, 공통 알람 스케줄링 로직이 앱별 구현에 직접 의존하지
 * 않는다.
 *
 * **사용 시점:** 반드시 `Application.onCreate()` 안에서, WorkManager 가 첫 Worker 를
 * 인스턴스화하기 전에 호출. Worker 가 구성 전에 돌면 lateinit 예외가 난다.
 *
 * **교차검증 공급자:** `WeatherProvider` 를 구현한 aggregator 를 주입하면 OWM+KMA,
 * OWM+NWS 같은 가중치 교차검증도 기존 Worker 로직 변경 없이 붙일 수 있다.
 */
object WeatherWakeRuntime {

    /** 알람 발동 시 타겟이 되는 BroadcastReceiver 클래스 */
    lateinit var alarmReceiverClass: Class<*>
        private set

    /** 상태바 알람 아이콘 탭 시 열릴 MainActivity 클래스 (setAlarmClock showIntent) */
    lateinit var mainActivityClass: Class<*>
        private set

    /** OWM / KMA / NWS / 크로스체크 aggregator — Worker 가 이것을 통해 날씨 조회 */
    lateinit var weatherProvider: WeatherProvider
        private set

    /**
     * 디버그 날씨 시뮬레이션 값 ("RAIN"/"SNOW"/"CLEAR"/""). 공백이면 실제 API 사용.
     * 호출부에서 BuildConfig.DEBUG 가드를 이미 적용한 값만 넘길 것.
     */
    var weatherOverride: String = ""
        private set

    fun configure(
        alarmReceiverClass: Class<*>,
        mainActivityClass: Class<*>,
        weatherProvider: WeatherProvider,
        weatherOverride: String = ""
    ) {
        this.alarmReceiverClass = alarmReceiverClass
        this.mainActivityClass  = mainActivityClass
        this.weatherProvider    = weatherProvider
        this.weatherOverride    = weatherOverride
    }

    /**
     * configure() 가 호출되었는지 여부. WorkManager 가 Application.onCreate 보다
     * 먼저 Worker 를 인스턴스화할 수 있는 콜드스타트(부팅 직후 pending work
     * 복원 등) 상황에서 Worker 가 lateinit 접근 전에 검사하는 가드용.
     *
     * false 면 Worker 는 Result.retry() 로 WorkManager backoff 사이클에 맡기고,
     * 몇 분 뒤 Application.onCreate 가 완료된 상태에서 재시도되어 정상 진행된다.
     */
    fun isConfigured(): Boolean =
        ::alarmReceiverClass.isInitialized &&
        ::mainActivityClass.isInitialized &&
        ::weatherProvider.isInitialized
}
