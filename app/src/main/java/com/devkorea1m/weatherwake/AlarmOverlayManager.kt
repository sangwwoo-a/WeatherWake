package com.devkorea1m.weatherwake

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.devkorea1m.weatherwake.databinding.AlarmOverlayBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WindowManager를 사용하여 화면이 꺼진 상태에서도 알람 오버레이를 표시.
 * SYSTEM_ALERT_WINDOW 권한으로 잠금화면/충전화면 위에 직접 뷰를 표시.
 *
 * 핵심 원리:
 *   TYPE_APPLICATION_OVERLAY + FLAG_TURN_SCREEN_ON + FLAG_SHOW_WHEN_LOCKED
 *   → 화면이 꺼져 있으면 즉시 화면을 켜고, 잠금화면 위에 오버레이를 표시.
 *   → deprecated SCREEN_BRIGHT_WAKE_LOCK 이 아니라 WindowManager 자체 flag 로 화면을 켜므로
 *     Samsung/Xiaomi 등 OEM 전원 관리 제한을 우회할 수 있다.
 */
class AlarmOverlayManager(private val context: Context) {

    private var overlayView: FrameLayout? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context)
        else true

    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * 알람 오버레이 표시.
     * BroadcastReceiver 에서 호출되므로 반드시 메인 스레드에서 WindowManager.addView 실행.
     */
    fun showAlarmOverlay(alarmId: Int, isMoved: Boolean, movedReason: String) {
        mainHandler.post { showAlarmOverlayInternal(alarmId, isMoved, movedReason) }
    }

    private fun showAlarmOverlayInternal(alarmId: Int, isMoved: Boolean, movedReason: String) {
        removeAlarmOverlay()
        if (!canDrawOverlays()) return

        // ── 화면 강제 켜기 (WakeLock) ────────────────────────────────────
        // SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP : deprecated 이지만
        //   여전히 동작하는 기기가 있으므로 보험으로 유지.
        // 오버레이의 FLAG_TURN_SCREEN_ON 과 이중으로 시도하여
        // 둘 중 하나라도 화면을 켜면 성공.
        @Suppress("DEPRECATION")
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WeatherWake:Overlay"
        )
        wl.acquire(15_000L)

        val themedContext = ContextThemeWrapper(context, R.style.Theme_WeatherWake)
        val binding = AlarmOverlayBinding.inflate(LayoutInflater.from(themedContext))
        val container = FrameLayout(themedContext).also { it.addView(binding.root) }

        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            format = PixelFormat.RGBA_8888

            // ★ 핵심 flag 조합 ★
            // FLAG_TURN_SCREEN_ON       : 화면이 꺼져 있으면 즉시 켠다
            // FLAG_SHOW_WHEN_LOCKED     : 잠금화면 위에 표시
            // FLAG_KEEP_SCREEN_ON       : 오버레이가 떠 있는 동안 화면 유지
            // FLAG_DISMISS_KEYGUARD     : non-secure 키가드 해제
            // FLAG_LAYOUT_IN_SCREEN     : 상태바 영역까지 전체 화면 차지
            // ※ FLAG_NOT_FOCUSABLE 을 넣지 않아야 버튼 터치가 가능하다
            @Suppress("DEPRECATION")
            flags = (
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            )

            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
        }

        // UI 데이터 바인딩
        binding.tvCurrentTime.text = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())
        if (isMoved && movedReason.isNotEmpty()) {
            val reasonText = movedReasonText(movedReason)
            binding.tvReason.text = context.getString(R.string.message_woke_earlier, reasonText)
            binding.tvReason.visibility = android.view.View.VISIBLE
        } else {
            binding.tvReason.visibility = android.view.View.GONE
        }

        // ── 버튼 리스너 ──────────────────────────────────────────────────
        // startService() 사용: 이미 foreground 상태인 AlarmService 에 action 만 전달.
        // startForegroundService() 를 쓰면 서비스가 종료된 상태에서 재시작될 때
        // startForeground() 를 재호출하지 않아 ForegroundServiceDidNotStartInTimeException 발생.
        binding.btnDismiss.setOnClickListener {
            sendAction(AlarmService.ACTION_DISMISS, alarmId)
            // dismiss 후 MainActivity 로 이동
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            removeAlarmOverlay()
        }
        binding.btnSnooze.setOnClickListener {
            sendAction(AlarmService.ACTION_SNOOZE, alarmId)
            removeAlarmOverlay()
        }

        try {
            windowManager.addView(container, params)
            overlayView = container
        } catch (_: Exception) { /* 권한 취소 등 */ }
    }

    fun removeAlarmOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    /** 저장된 코드("RAIN", "SNOW" 등)를 표시용 문자열로 변환 */
    private fun movedReasonText(code: String): String = when (code) {
        "RAIN" -> context.getString(R.string.moved_reason_rain)
        "SNOW" -> context.getString(R.string.moved_reason_snow)
        else -> code  // 하위호환: 이미 한국어가 저장된 기존 데이터는 그대로 표시
    }

    private fun sendAction(action: String, alarmId: Int) {
        context.startService(
            Intent(context, AlarmService::class.java).apply {
                this.action = action
                putExtra(AlarmService.EXTRA_ALARM_ID_ACTION, alarmId)
            }
        )
    }

    companion object {
        @Volatile private var instance: AlarmOverlayManager? = null
        fun getInstance(context: Context): AlarmOverlayManager =
            instance ?: synchronized(this) {
                instance ?: AlarmOverlayManager(context.applicationContext).also { instance = it }
            }
    }
}
