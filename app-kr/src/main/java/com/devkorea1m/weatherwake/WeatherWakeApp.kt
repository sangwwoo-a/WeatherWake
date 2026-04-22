package com.devkorea1m.weatherwake

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager

class WeatherWakeApp : Application() {

    override fun onCreate() {
        super.onCreate()
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
