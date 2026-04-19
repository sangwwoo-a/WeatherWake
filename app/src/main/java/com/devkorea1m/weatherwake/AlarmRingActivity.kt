package com.devkorea1m.weatherwake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.devkorea1m.weatherwake.databinding.ActivityAlarmRingBinding
import com.devkorea1m.weatherwake.util.AlarmScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 알람 UI 화면 (Fallback용).
 *
 * 주로 AlarmOverlayManager의 WindowManager 오버레이가 UI 표시를 담당.
 * 이 Activity는 다음 경우에 사용됨:
 * 1. SYSTEM_ALERT_WINDOW 권한이 없을 때 (fallback)
 * 2. 사용자가 화면 잠금 해제 후 알림을 탭했을 때
 *
 * 흐름:
 *  - 끄기 버튼 → AlarmService로 ACTION_DISMISS 전송 → AlarmService가 소리·진동 정지 후
 *    ACTION_FINISH_RING 브로드캐스트 → 이 Activity가 finish()
 *  - 5분 더 버튼 → AlarmService로 ACTION_SNOOZE 전송 → 동일한 흐름으로 finish()
 *  - 알림 "끄기"/"5분 더" 버튼 → AlarmActionReceiver → AlarmService → ACTION_FINISH_RING → finish()
 */
class AlarmRingActivity : AppCompatActivity() {

    private lateinit var b: ActivityAlarmRingBinding
    private var alarmId = -1
    private var isDismissing = false   // 끄기(dismiss)인지 스누즈인지 구분

    /** AlarmService가 완전히 종료됐을 때 받아서 화면을 닫는 수신기 */
    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isDismissing) {
                // 알람 끄기 → 앱 첫 화면(MainActivity)으로 이동
                startActivity(
                    Intent(this@AlarmRingActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                )
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 잠금 화면 위에 Activity 표시 (API 27+)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // 구형 API 대비 window flag도 추가
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        b = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Edge-to-edge insets 처리
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val isMoved     = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_MOVED, false)
        val movedReason = intent.getStringExtra(AlarmScheduler.EXTRA_MOVED_REASON) ?: ""

        // 현재 시각 표시
        b.tvCurrentTime.text = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())

        // 앞당김 이유 표시
        if (isMoved && movedReason.isNotEmpty()) {
            val reasonText = movedReasonText(movedReason)
            b.tvReason.text = getString(R.string.message_woke_earlier, reasonText)
            b.tvReason.visibility = android.view.View.VISIBLE
        } else {
            b.tvReason.visibility = android.view.View.GONE
        }

        b.btnDismiss.setOnClickListener {
            isDismissing = true
            sendActionToService(AlarmService.ACTION_DISMISS)
        }
        b.btnSnooze.setOnClickListener { sendActionToService(AlarmService.ACTION_SNOOZE) }

        // ACTION_FINISH_RING 수신기를 onCreate에서 등록하고 onDestroy에서 해제한다.
        // onResume/onPause 쌍을 사용하면 dismiss 버튼 클릭 직후 잠깐 onPause가 호출될 때
        // 수신기가 해제되어 브로드캐스트를 놓치는 버그가 발생할 수 있다.
        val filter = IntentFilter(AlarmService.ACTION_FINISH_RING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(finishReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(finishReceiver, filter)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(finishReceiver) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 동일 인스턴스로 새 알람이 들어오면 alarmId 갱신
        alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
    }

    // ─────────────────────────────────────────────────────────

    /**
     * 끄기 / 스누즈 액션을 AlarmService로 전달.
     * AlarmService가 소리·진동 정지 → ACTION_FINISH_RING 브로드캐스트 → finish() 순서로 처리.
     */
    /** 저장된 코드("RAIN", "SNOW" 등)를 표시용 문자열로 변환 */
    private fun movedReasonText(code: String): String = when (code) {
        "RAIN" -> getString(R.string.moved_reason_rain)
        "SNOW" -> getString(R.string.moved_reason_snow)
        else -> code  // 하위호환: 이미 한국어가 저장된 기존 데이터는 그대로 표시
    }

    private fun sendActionToService(action: String) {
        val intent = Intent(this, AlarmService::class.java).apply {
            this.action = action
            putExtra(AlarmService.EXTRA_ALARM_ID_ACTION, alarmId)
        }
        // startService() 사용: dismiss/snooze 액션은 foreground 승격 불필요
        // startForegroundService()를 쓰면 서비스가 종료된 상태에서 재시작될 때
        // startForeground()를 호출하지 않아 ForegroundServiceDidNotStartInTimeException 발생
        startService(intent)
    }
}
