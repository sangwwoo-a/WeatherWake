package com.devkorea1m.weatherwake

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.devkorea1m.weatherwake.BuildConfig
import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.repository.AppResult
import com.devkorea1m.weatherwake.data.repository.WeatherRepository
import com.devkorea1m.weatherwake.databinding.ActivityMainBinding
import com.devkorea1m.weatherwake.util.LocationHelper
import com.devkorea1m.weatherwake.viewmodel.AlarmViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val viewModel: AlarmViewModel by viewModels()
    private val adapter = AlarmAdapter(
        onToggle        = { viewModel.toggleEnabled(it) },
        onWeatherToggle = { viewModel.toggleWeather(it) },
        onEdit          = { startActivity(Intent(this, AlarmSettingActivity::class.java).apply {
            putExtra("alarm_id", it.id)
        })},
        onDelete = { alarm ->
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.message_delete_alarm_confirm, alarm.label))
                .setPositiveButton(getString(R.string.action_delete_confirm)) { _, _ -> viewModel.deleteAlarm(alarm) }
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show()
        },
        onSelectionChanged = { isActive, count -> updateSelectionBar(isActive, count) }
    )

    // 알림 권한 요청 런처 (Android 13+)
    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 허용/거부 무관하게 다음 단계(정확한 알람 권한)로 진행
            checkExactAlarmPermission()
        }

    // 위치 권한 요청 런처
    private val locationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 허용 여부 무관, 위치 단계 완료 → 다음 권한 단계(전체화면 intent)로
            refreshWeatherCard()
            checkFullScreenIntentPermission()
        }

    // 설정 화면에서 돌아왔는지 추적
    private var waitingForFullScreenIntentPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Edge-to-edge insets 처리
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(b.toolbar)

        b.rvAlarms.adapter = adapter
        viewModel.alarms.observe(this) { adapter.submitList(it) }

        b.fabAdd.setOnClickListener {
            startActivity(Intent(this, AlarmSettingActivity::class.java))
        }

        b.btnLocation.setOnClickListener {
            startActivity(Intent(this, LocationSettingActivity::class.java))
        }

        // 날씨 새로고침 — 알람은 원래 시각 90분 전 자동 체크지만,
        // 사용자가 수시로 현재 날씨를 확인하고 싶을 수 있어 수동 새로고침 제공.
        b.btnRefreshWeather.setOnClickListener {
            if (!b.btnRefreshWeather.isEnabled) return@setOnClickListener
            // 연타 방지: 2초간 비활성 + 아이콘 회전 피드백
            b.btnRefreshWeather.isEnabled = false
            b.btnRefreshWeather.animate()
                .rotationBy(360f)
                .setDuration(700)
                .withEndAction { b.btnRefreshWeather.rotation = 0f }
                .start()
            b.btnRefreshWeather.postDelayed({ b.btnRefreshWeather.isEnabled = true }, 2000)
            refreshWeatherCard()
        }

        // 날씨 데이터 출처 — KMA + OWM 각각 별도 링크로 ClickableSpan 주입
        setupWeatherAttribution()

        // DevKorea1m 브랜드 워터마크 — "Built by DevKorea[1m]" 형태, "1m"은 YouTube Red, 탭 시 홈페이지
        setupBrandWatermark()

        // 선택된 알람 일괄 삭제
        b.btnDeleteSelected.setOnClickListener {
            val ids = adapter.getSelectedIds()
            if (ids.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.message_delete_alarms_confirm, ids.size))
                .setPositiveButton(getString(R.string.action_delete_confirm)) { _, _ ->
                    viewModel.deleteAlarmsByIds(ids)
                    adapter.clearSelection()
                }
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show()
        }

        // 권한 순서대로 요청: 전체화면(잠금화면 알람) → 알림 → 정확한 알람 → 위치
        requestRequiredPermissions()
    }

    override fun onResume() {
        super.onResume()
        // 전체화면 권한 설정 화면에서 돌아온 경우
        if (waitingForFullScreenIntentPermission) {
            waitingForFullScreenIntentPermission = false
            checkNotificationPermission()
        }
        if (hasLocationPermission()) refreshWeatherCard()
    }

    // ─── 뒤로가기: 선택 모드 해제 ────────────────────────
    override fun onBackPressed() {
        if (adapter.isSelectionMode) {
            adapter.clearSelection()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ─── 선택 모드 UI 업데이트 ───────────────────────────
    private fun updateSelectionBar(isActive: Boolean, count: Int) {
        if (isActive) {
            b.selectionBar.visibility = android.view.View.VISIBLE
            b.fabAdd.hide()
            b.tvSelectionCount.text = getString(R.string.label_alarm_count_selected, count)
        } else {
            b.selectionBar.visibility = android.view.View.GONE
            b.fabAdd.show()
        }
    }

    // ─── 권한 순차 요청 ──────────────────────────────────
    // 순서: ① 위치 → ② 전체화면 → ③ 알림 → ④ 정확한 알람 → ⑤ 배터리 최적화 예외
    // 위치를 맨 앞에 둔 이유: 런타임 시스템 다이얼로그 (앱 화면 유지) 라서 사용자가
    // 앱을 떠나지 않고 즉시 응답 가능. 나머지는 전부 시스템 설정 화면으로 이동하는
    // 특수 권한이라 뒤로 배치 — 설치 직후 첫 인상에서 앱을 여러 번 떠나지 않게 함.
    // 또한 위치 없으면 MainActivity 상단 날씨 카드가 동작 안 해 UX 로 "앱이 비어있음"
    // 으로 느껴지므로 첫 단계에서 해결.
    // SYSTEM_ALERT_WINDOW(오버레이) 권한은 표준 full-screen intent 경로 도입으로 더 이상 사용하지 않음.

    private fun requestRequiredPermissions() {
        ensureLocationPermission()
    }

    private fun checkFullScreenIntentPermission() {
        // Android 14(UPSIDE_DOWN_CAKE)부터 USE_FULL_SCREEN_INTENT는 사용자가
        // 설정에서 직접 허용해야 하는 특별 권한 (런타임 requestPermission 불가).
        // 설치 직후 앱 첫 실행 시 가장 먼저 안내한다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.message_full_screen_permission_title))
                    .setMessage(getString(R.string.message_full_screen_permission_body))
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.action_allow_now)) { _, _ ->
                        waitingForFullScreenIntentPermission = true
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }
                    .setNegativeButton(getString(R.string.action_allow_later)) { _, _ -> checkNotificationPermission() }
                    .show()
                return
            }
        }
        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        // 2단계: POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            checkExactAlarmPermission()
        }
    }

    private fun checkExactAlarmPermission() {
        // 3단계: 정확한 알람 권한 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.message_exact_alarm_permission_title))
                    .setMessage(getString(R.string.message_exact_alarm_permission_body))
                    .setPositiveButton(getString(R.string.action_open_settings)) { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")))
                    }
                    .setNegativeButton(getString(R.string.action_allow_later)) { _, _ -> checkBatteryOptimization() }
                    .show()
                return
            }
        }
        checkBatteryOptimization()
    }

    private fun checkBatteryOptimization() {
        // 4단계: 배터리 최적화 예외 — 없으면 시스템 절전으로 알람이 지연/누락될 수 있음
        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.message_battery_opt_title))
                .setMessage(getString(R.string.message_battery_opt_body))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.action_open_settings)) { _, _ ->
                    runCatching {
                        @SuppressLint("BatteryLife")
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName"))
                        startActivity(intent)
                    }
                }
                .setNegativeButton(getString(R.string.action_allow_later), null)
                .show()
        }
        // 권한 체인 종료. 위치는 맨 앞(requestRequiredPermissions → ensureLocationPermission)에서 이미 처리됨.
    }

    // ─── 권한 헬퍼 ───────────────────────────────────────

    private fun weatherEmoji(type: WeatherConditionType) = when (type) {
        WeatherConditionType.RAIN  -> "☔"
        WeatherConditionType.SNOW  -> "❄️"
        WeatherConditionType.CLEAR -> "☀️"
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun ensureLocationPermission() {
        if (hasLocationPermission()) {
            // 이미 허용됨 → 바로 다음 권한 단계로, 날씨 카드는 병렬로 갱신
            refreshWeatherCard()
            checkFullScreenIntentPermission()
        } else {
            // 런처의 onResult 에서 checkFullScreenIntentPermission 으로 체인 이어짐
            locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    // ─── 날씨 카드 ────────────────────────────────────────

    private fun refreshWeatherCard() {
        lifecycleScope.launch {
            runCatching {
                val latLon = if (LocationHelper.isUseGps(this@MainActivity) && hasLocationPermission()) {
                    LocationHelper.getCurrentLocation(this@MainActivity)
                        ?: LocationHelper.getSavedLocation(this@MainActivity)
                } else {
                    LocationHelper.getSavedLocation(this@MainActivity)
                }

                if (latLon == null) {
                    b.tvWeatherStatus.text = getString(R.string.message_location_set_to)
                    b.tvWeatherCity.text   = ""
                    b.tvWeatherTemp.text   = ""
                    return@runCatching
                }

                b.tvWeatherCity.text   = "📍 ${latLon.label}"
                b.tvWeatherStatus.text = getString(R.string.message_checking_weather)

                when (val result = com.devkorea1m.weatherwake.runtime.WeatherWakeRuntime
                    .weatherProvider.getCurrentWeather(lat = latLon.lat, lon = latLon.lon)) {
                    is AppResult.Success -> {
                        val weather = result.data
                        b.tvWeatherStatus.text = weather.description
                        b.tvWeatherTemp.text   = "%.1f°C".format(weather.tempCelsius)
                        // 수동 선택 시 사용자가 고른 라벨 유지, GPS 자동감지일 때만 OWM 반환값 사용
                        // OWM은 좌표 인근의 임의 dong 단위를 반환할 수 있어 사용자 의도와 어긋날 수 있음
                        b.tvWeatherCity.text   = if (LocationHelper.isUseGps(this@MainActivity)) {
                            "📍 ${weather.cityName}"
                        } else {
                            "📍 ${latLon.label}"
                        }
                        b.tvWeatherEmoji.text  = weatherEmoji(weather.conditionType)
                    }
                    is AppResult.NetworkError -> {
                        b.tvWeatherStatus.text = getString(R.string.message_weather_server_error, result.code)
                        b.tvWeatherTemp.text   = ""
                        b.tvWeatherEmoji.text  = "🌡"
                    }
                    is AppResult.Error -> {
                        b.tvWeatherStatus.text = result.message
                        b.tvWeatherTemp.text   = ""
                        b.tvWeatherEmoji.text  = "🌡"
                    }
                }
            }.onFailure { e ->
                // 어떤 예외도 앱을 죽이지 않도록 방어
                e.printStackTrace()
                runCatching {
                    b.tvWeatherStatus.text = getString(R.string.message_error_occurred)
                    b.tvWeatherTemp.text   = ""
                }
            }
        }
    }

    /**
     * 상단 날씨 카드 하단의 출처 표시 설정.
     *
     * "기상청 국가기후데이터센터 + OpenWeatherMap 교차 검증 중" — 기관명 각각을
     * ClickableSpan 으로 감싸서 탭 시 해당 공식 사이트가 열린다. 두 공급자의
     * 약관(공공데이터포털 · OWM) 이 모두 표시·링크를 요구하므로 단일 링크 대신
     * 분리 링크 방식.
     *
     * 문자열에서 기관명 위치는 indexOf 로 런타임에 찾는다 — 로케일마다 어순이
     * 달라도 기관명 자체는 그대로 박혀있어 강건. 일치하지 않으면 span 만 생략
     * (텍스트는 그대로 보이므로 fail-safe).
     */
    private fun setupWeatherAttribution() {
        val full    = getString(R.string.weather_attribution)
        val kmaName = getString(R.string.weather_attribution_kma)
        val owmName = getString(R.string.weather_attribution_owm)

        val sb = SpannableStringBuilder(full)

        // 기본 ClickableSpan 은 시스템 링크색(파란색) + 밑줄을 강제로 덧씌워 주변 텍스트와
        // 시각적 불일치가 생긴다. 여기선 공급자 표기 자체가 문장의 일부로 읽히길 원하므로
        // updateDrawState 로 링크 스타일을 제거 — TextView 의 원래 color/size 가 그대로
        // 상속되어 "교차 검증 중" 과 동일한 폰트로 보이면서 여전히 탭 가능.
        fun linkSpan(url: String) = object : ClickableSpan() {
            override fun onClick(widget: View) { openUrl(url) }
            override fun updateDrawState(ds: android.text.TextPaint) {
                ds.isUnderlineText = false
                // color 는 일부러 건드리지 않음 → TextView 의 textColor(text_hint) 가 유지됨
            }
        }

        val kmaStart = full.indexOf(kmaName)
        if (kmaStart >= 0) {
            sb.setSpan(
                linkSpan("https://data.kma.go.kr"),
                kmaStart, kmaStart + kmaName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val owmStart = full.indexOf(owmName)
        if (owmStart >= 0) {
            sb.setSpan(
                linkSpan("https://openweathermap.org"),
                owmStart, owmStart + owmName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        b.tvWeatherAttribution.text = sb
        // ClickableSpan 이 탭을 받으려면 LinkMovementMethod 가 필수
        b.tvWeatherAttribution.movementMethod = LinkMovementMethod.getInstance()
    }

    /** URL 열기 — 크래시 방지 (브라우저 없는 기기 대응) */
    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    /**
     * DevKorea1m 브랜드 워터마크 설정.
     * "Built by" + 4px spacer + "DevKorea"(onSurface) + "1m"(YouTube Red)
     * - "1m" 색상은 라이트/다크 모드 무관 #FF0000 고정 (브랜드 가이드)
     * - 탭 시 브랜드 홈페이지 열기, 실패 시 앱 크래시 방지
     * - contentDescription으로 TalkBack 대응
     */
    private fun setupBrandWatermark() {
        val builtBy = getString(R.string.brand_built_by)
        val prefix  = getString(R.string.brand_name_prefix)   // "DevKorea"
        val accent  = getString(R.string.brand_name_accent)   // "1m"

        val builtByColor = ContextCompat.getColor(this, R.color.brand_builtby_dark)
        val baseColor    = ContextCompat.getColor(this, R.color.brand_base_dark)
        val accentColor  = ContextCompat.getColor(this, R.color.brand_accent)

        val sb = SpannableStringBuilder()
        // "Built by " (Medium weight, 회색)
        val builtByStart = sb.length
        sb.append(builtBy)
        sb.append(" ")
        sb.setSpan(ForegroundColorSpan(builtByColor), builtByStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // "DevKorea" (SemiBold, onSurface)
        val prefixStart = sb.length
        sb.append(prefix)
        sb.setSpan(ForegroundColorSpan(baseColor), prefixStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD), prefixStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // "1m" (YouTube Red 고정)
        val accentStart = sb.length
        sb.append(accent)
        sb.setSpan(ForegroundColorSpan(accentColor), accentStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD), accentStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        b.tvBrandWatermark.text = sb

        b.tvBrandWatermark.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.brand_url))))
            }
        }
    }
}
