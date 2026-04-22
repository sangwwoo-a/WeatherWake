package com.devkorea1m.weatherwake

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import com.devkorea1m.weatherwake.data.repository.WeatherRepository
import com.devkorea1m.weatherwake.runtime.WeatherWakeRuntime

class WeatherWakeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // :core 의 AlarmScheduler / WeatherCheckWorker 가 app-kr 의 구체 타입과
        // 날씨 제공자를 찾을 수 있도록 Runtime 에 등록. WorkManager 가 첫 Worker 를
        // 인스턴스화하기 전에 반드시 완료돼야 하므로 onCreate 최상단에 배치.
        WeatherWakeRuntime.configure(
            alarmReceiverClass = AlarmReceiver::class.java,
            mainActivityClass  = MainActivity::class.java,
            weatherProvider    = WeatherRepository(BuildConfig.OWM_API_KEY),
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
    }
}
