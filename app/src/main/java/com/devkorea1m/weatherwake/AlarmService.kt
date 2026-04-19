package com.devkorea1m.weatherwake

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.devkorea1m.weatherwake.data.db.AppDatabase
import com.devkorea1m.weatherwake.util.AlarmScheduler
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * 알람이 울릴 때 실행되는 Foreground Service.
 *
 * AlarmReceiver → AlarmOverlayManager (WindowManager 오버레이)
 *              → AlarmService (소리 + 진동 + 상태바 알림)
 *
 * 알람 UI는 AlarmOverlayManager가 WindowManager.addView()로 처리하고,
 * AlarmService는 소리/진동/상태바 알림만 담당.
 *
 * 알림 액션 버튼(끄기 / 5분 더)은 AlarmActionReceiver → AlarmService(ACTION)로 처리.
 */
class AlarmService : Service() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var currentAlarmId   = -1
    private var currentRepeatDays = -1  // 0 = 반복 없음 (1회성)

    /**
     * PARTIAL_WAKE_LOCK : 알람 재생 중 CPU를 깨어있게 유지.
     * 화면을 켜는 것은 AlarmReceiver의 PARTIAL_WAKE_LOCK과
     * AlarmOverlayManager의 WindowManager.TYPE_APPLICATION_OVERLAY가 함께 담당.
     */
    @Suppress("DEPRECATION")
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> {
                // 혹시라도 서비스가 신규 시작된 경우 startForeground를 먼저 호출해야
                // ForegroundServiceDidNotStartInTimeException 방지 가능.
                // (정상 흐름은 AlarmActionReceiver/AlarmRingActivity 모두 startService() 사용)
                if (ringtone == null) ensureForeground()
                dismissAlarm()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE  -> {
                if (ringtone == null) ensureForeground()
                snoozeAlarm(intent)
                return START_NOT_STICKY
            }
        }

        // 최초 알람 시작
        currentAlarmId    = intent?.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1) ?: -1
        currentRepeatDays = intent?.getIntExtra(AlarmScheduler.EXTRA_REPEAT_DAYS, -1) ?: -1
        val soundUri      = intent?.getStringExtra(AlarmScheduler.EXTRA_SOUND_URI) ?: ""
        val isMoved       = intent?.getBooleanExtra(AlarmScheduler.EXTRA_IS_MOVED, false) ?: false
        val movedReason   = intent?.getStringExtra(AlarmScheduler.EXTRA_MOVED_REASON) ?: ""

        startForegroundWithNotification(currentAlarmId, isMoved, movedReason)
        playSound(soundUri)
        playVibration()
        // AlarmRingActivity 실행은 startForegroundWithNotification()의 fullScreenIntent가 담당.
        // startActivity()를 별도로 호출하면 fullScreenIntent와 경합해 화면이 두 번 표시되는 버그 발생.

        return START_NOT_STICKY
    }

    // ─── 알림 생성 ────────────────────────────────────────────────────

    private fun startForegroundWithNotification(alarmId: Int, isMoved: Boolean, movedReason: String) {
        // ── WakeLock 획득: CPU 유지 ──────────────────────────────────────
        // 화면을 켜는 것은 AlarmOverlayManager의 WindowManager가 담당.
        // 여기서는 알람이 울리는 동안 CPU가 sleep으로 빠지지 않도록 유지한다.
        @Suppress("DEPRECATION")
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock?.takeIf { it.isHeld }?.release()
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WeatherWake:AlarmWakeLock"
        ).also { it.acquire(10 * 60 * 1000L) } // 최대 10분 (dismiss/snooze 시 명시적 해제)

        val contentText = if (isMoved && movedReason.isNotEmpty()) movedReason else getString(R.string.notification_alarm_ringing)

        // 알람 화면으로 이동하는 PendingIntent (알림 터치 시)
        val ringPi = PendingIntent.getActivity(
            this, alarmId,
            Intent(this, AlarmRingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_IS_MOVED, isMoved)
                putExtra(AlarmScheduler.EXTRA_MOVED_REASON, movedReason)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 끄기 액션 PendingIntent
        val dismissPi = PendingIntent.getBroadcast(
            this, alarmId + 10000,
            Intent(this, AlarmActionReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_ALARM_ID_ACTION, alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 5분 더 액션 PendingIntent
        val snoozePi = PendingIntent.getBroadcast(
            this, alarmId + 20000,
            Intent(this, AlarmActionReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_ALARM_ID_ACTION, alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, WeatherWakeApp.CHANNEL_ALARM)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("WeatherWake ⏰")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(ringPi, true)
            .setContentIntent(ringPi)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .addAction(0, getString(R.string.action_snooze_5min), snoozePi)
            .addAction(0, getString(R.string.action_dismiss), dismissPi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // ※ 알람 UI 표시는 AlarmReceiver → AlarmOverlayManager에서 처리.
        //   fullScreenIntent는 fallback용으로만 유지 (권한 없을 때 등).
    }

    // ─── 방어적 foreground 보장 ──────────────────────────────────────

    /**
     * DISMISS/SNOOZE 가 신규 서비스 시작으로 들어왔을 때 (예: 이전 서비스가 이미 종료된 상태)
     * startForeground()를 한 번 호출해 5초 제한을 충족시킨다.
     * 이후 dismissAlarm()/snoozeAlarm() 내부에서 stopForeground()가 호출된다.
     */
    private fun ensureForeground() {
        val silentNotification = NotificationCompat.Builder(this, WeatherWakeApp.CHANNEL_ALARM)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("WeatherWake")
            .setContentText(getString(R.string.notification_alarm_ending))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, silentNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, silentNotification)
        }
    }

    // ─── 소리 / 진동 ─────────────────────────────────────────────────

    private fun playSound(soundUri: String) {
        val uri: Uri = if (soundUri.isNotBlank()) Uri.parse(soundUri)
                       else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, uri)
        ringtone?.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) / 2,
                0
            )
        }
        ringtone?.isLooping = true
        ringtone?.play()
    }

    @Suppress("DEPRECATION")
    private fun playVibration() {
        vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0))
    }

    // ─── 끄기 / 스누즈 ───────────────────────────────────────────────

    private fun dismissAlarm() {
        ringtone?.stop()
        vibrator?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        // 오버레이 제거 (AlarmOverlayManager 에서 표시한 경우)
        AlarmOverlayManager.getInstance(this).removeAlarmOverlay()
        // AlarmRingActivity 종료 브로드캐스트 (fallback Activity 에서 표시한 경우)
        sendBroadcast(Intent(ACTION_FINISH_RING))

        // 반복 없음 (1회성) 알람은 울리고 나면 자동으로 비활성화.
        // runBlocking 으로 DB 업데이트를 완전히 마친 뒤 stopSelf() 를 호출해야
        // stopSelf()→프로세스 종료 경쟁에서 업데이트가 누락되지 않는다.
        if (currentRepeatDays == 0 && currentAlarmId != -1) {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                AppDatabase.getInstance(applicationContext)
                    .alarmDao()
                    .setEnabled(currentAlarmId, false)
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun snoozeAlarm(intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID_ACTION, currentAlarmId)
        ringtone?.stop()
        vibrator?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        // 오버레이 제거
        AlarmOverlayManager.getInstance(this).removeAlarmOverlay()

        // 5분 후 알람 재등록
        val snoozeMs = System.currentTimeMillis() + SNOOZE_DURATION_MS
        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_IS_MOVED, false)
            putExtra(AlarmScheduler.EXTRA_MOVED_REASON, "")
            putExtra(AlarmScheduler.EXTRA_SOUND_URI,
                ringtone?.let { "" } ?: "")  // 원래 소리 유지 어려우므로 기본음
            // 1회성 알람 여부를 스누즈 후 재발동 시에도 유지해야 자동 비활성화가 동작함
            putExtra(AlarmScheduler.EXTRA_REPEAT_DAYS, currentRepeatDays)
        }
        val snoozePi = PendingIntent.getBroadcast(
            this, alarmId + 30000, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            val alarmManager = getSystemService(AlarmManager::class.java)
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(snoozeMs, snoozePi), snoozePi
            )
        } catch (_: SecurityException) {}

        sendBroadcast(Intent(ACTION_FINISH_RING))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ringtone?.stop()
        vibrator?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID       = 1001
        const val ACTION_DISMISS        = "com.devkorea1m.weatherwake.ACTION_DISMISS"
        const val ACTION_SNOOZE         = "com.devkorea1m.weatherwake.ACTION_SNOOZE"
        const val ACTION_FINISH_RING    = "com.devkorea1m.weatherwake.ACTION_FINISH_RING"
        const val EXTRA_ALARM_ID_ACTION = "alarm_id_action"
        private const val SNOOZE_DURATION_MS = 5 * 60 * 1000L  // 5분
    }
}
