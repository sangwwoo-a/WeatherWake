package com.devkorea1m.weatherwake

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import com.devkorea1m.weatherwake.data.kma.KmaWeatherProvider
import com.devkorea1m.weatherwake.data.nws.NwsWeatherProvider
import com.devkorea1m.weatherwake.data.repository.WeatherRepository
import com.devkorea1m.weatherwake.domain.RegionalWeatherProvider
import com.devkorea1m.weatherwake.runtime.WeatherWakeRuntime

class WeatherWakeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // :core 의 AlarmScheduler / WeatherCheckWorker 가 app-kr 의 구체 타입과
        // 날씨 제공자를 찾을 수 있도록 Runtime 에 등록. WorkManager 가 첫 Worker 를
        // 인스턴스화하기 전에 반드시 완료돼야 하므로 onCreate 최상단에 배치.
        // WeatherProvider 조합 — 좌표에 따라 적합한 지역 특화 공급자로 자동 라우팅.
        //   KR  → CrossValidating(KMA, OWM)   (안전우선 threshold 0.3)
        //   US  → CrossValidating(NWS, OWM)
        //   그 외 → OWM 단독
        // 여행·출장 시나리오에서 앱 재시작 없이 다음 날씨 조회부터 자동 전환됨.
        // NWS 는 API 키 대신 식별 가능한 User-Agent 를 필수로 요구 — 브랜드 도메인을 연락처로 삽입.
        val provider = RegionalWeatherProvider(
            kmaProvider = KmaWeatherProvider(BuildConfig.KMA_SERVICE_KEY),
            nwsProvider = NwsWeatherProvider(NWS_USER_AGENT),
            owmProvider = WeatherRepository(BuildConfig.OWM_API_KEY)
        )
        WeatherWakeRuntime.configure(
            alarmReceiverClass = AlarmReceiver::class.java,
            mainActivityClass  = MainActivity::class.java,
            weatherProvider    = provider,
            weatherOverride    = if (BuildConfig.DEBUG) BuildConfig.WEATHER_OVERRIDE else ""
        )
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        // 이전 채널 삭제: Android 는 한 번 생성된 채널의 importance 를
        // 코드로 올릴 수 없으므로, 이전 버전에서 만든 채널이 낮은 importance 로
        // 캐시되어 fullScreenIntent 가 무시되는 문제를 방지.
        nm.deleteNotificationChannel("weatherwake_alarm")   // v1 이전 ID

        // 알람 채널 — 최고 중요도, 방해금지 우회, 잠금 화면 표시
        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)   // 진동은 Service에서 직접 제어
            setSound(null, null)     // 소리도 Service에서 직접 제어
        }
        nm.createNotificationChannel(alarmChannel)
    }

    companion object {
        const val CHANNEL_ALARM = "weatherwake_alarm_v2"

        /**
         * NWS 필수 User-Agent. 보안 이슈나 남용 탐지 시 NWS 가 연락할 수 있도록
         * "앱이름 (연락처)" 형식 권장. 개인 이메일 노출을 피해 브랜드 도메인 사용.
         */
        private const val NWS_USER_AGENT = "WeatherWake Android (https://devkorea1m.com)"
    }
}
