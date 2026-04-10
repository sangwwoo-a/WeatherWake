package com.devkorea1m.weatherwake

import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.appcompat.app.AppCompatActivity
import com.devkorea1m.weatherwake.databinding.ActivityAlarmRingBinding
import com.devkorea1m.weatherwake.util.AlarmScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmRingActivity : AppCompatActivity() {

    private lateinit var b: ActivityAlarmRingBinding
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(b.root)

        val isMoved     = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_MOVED, false)
        val movedReason = intent.getStringExtra(AlarmScheduler.EXTRA_MOVED_REASON) ?: ""
        val soundUri    = intent.getStringExtra(AlarmScheduler.EXTRA_SOUND_URI) ?: ""

        // 현재 시각 표시
        b.tvCurrentTime.text = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())

        // 앞당김 이유 표시
        if (isMoved && movedReason.isNotEmpty()) {
            b.tvReason.text = movedReason + "\n더 일찍 깨웠어요 ⏰"
            b.tvReason.visibility = android.view.View.VISIBLE
        } else {
            b.tvReason.visibility = android.view.View.GONE
        }

        // 알람 소리 — 선택한 음원 우선, 없으면 기본 알람음
        val uri: Uri = if (soundUri.isNotBlank()) {
            Uri.parse(soundUri)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
        ringtone = RingtoneManager.getRingtone(this, uri)
        ringtone?.play()

        // 진동 (파형 반복)
        @Suppress("DEPRECATION")
        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0)
        )

        b.btnDismiss.setOnClickListener { dismissAlarm() }
        b.btnSnooze.setOnClickListener  { snoozeAlarm() }
    }

    private fun dismissAlarm() {
        ringtone?.stop()
        finish()
    }

    private fun snoozeAlarm() {
        // 5분 뒤 다시 울림 (간단 구현)
        ringtone?.stop()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        ringtone?.stop()
    }
}
