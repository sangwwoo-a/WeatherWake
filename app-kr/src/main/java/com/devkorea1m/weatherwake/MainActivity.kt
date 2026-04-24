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
import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.repository.AppResult
import com.devkorea1m.weatherwake.domain.Region
import com.devkorea1m.weatherwake.domain.WeatherSource
import com.devkorea1m.weatherwake.databinding.ActivityMainBinding
import com.devkorea1m.weatherwake.BuildConfig
import com.devkorea1m.weatherwake.util.LatLon
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

        // 날씨 데이터 출처 — 초기 placeholder (로케일 기본값). 실제 attribution 은
        // refreshWeatherCard() 가 공급자 호출 후 snapshot.sources 로 다시 주입.
        // 여기서 Region 으로 추측하는 이유: 첫 프레임에 "OpenWeatherMap 제공" 같은
        // 텍스트가 빈 공간 없이 보이도록 하기 위함 (한 프레임이라도 공란 방지).
        setupWeatherAttributionByRegion(Region.KR)

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

    // ─── 툴바 오버플로 메뉴 (개인정보처리방침 / 이용약관) ────
    // Play Console 정책상 법적 문서는 앱 내에서 접근 가능해야 함.
    // 메인 화면이 이미 빡빡해서 3점 메뉴로만 노출.
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        // 디버그 좌표 주입 아이템은 debug 빌드에서만 노출
        menu.findItem(R.id.menu_debug_inject_coords)?.isVisible = BuildConfig.DEBUG
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean = when (item.itemId) {
        R.id.menu_privacy_policy -> {
            openUrl(getString(R.string.url_privacy_policy)); true
        }
        R.id.menu_terms_of_service -> {
            openUrl(getString(R.string.url_terms_of_service)); true
        }
        R.id.menu_debug_inject_coords -> {
            showDebugCoordInjector(); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ─── 디버그 전용: 테스트 좌표 주입 ─────────────────────────
    // Region 바운딩 박스 / 공급자 라우팅 / sentinel 가드 동작을 실기기에서
    // 확인하기 위한 도구. GPS 실제 이동 없이 특정 국가 좌표를 시뮬레이션.
    //
    // 동작:
    //  1) GPS 자동 감지를 꺼 저장 좌표만 사용하도록 스위치
    //  2) 선택한 도시 좌표를 SharedPreferences 에 저장
    //  3) 사용자가 "날씨 새로고침" 버튼을 눌러 UI 갱신 확인
    //
    // 리스트 구성: PR #25 에서 다룬 회귀 케이스 — KR 경계(큐슈)·US 경계
    // (캐나다·멕시코)·정상 케이스(서울·SF)·기타(홍콩) 를 망라.
    private fun showDebugCoordInjector() {
        val presets = listOf<Triple<String, Double, Double>>(
            // 정상 케이스 — 기존 동작 회귀 확인용
            Triple("🇰🇷 Seoul (KR 본토)",         37.5665, 126.9780),
            Triple("🇰🇷 Dokdo (독도·울릉박스)",   37.2414, 131.8669),
            Triple("🇺🇸 San Francisco (US)",     37.7749, -122.4194),
            Triple("🇺🇸 Juneau (AK 판핸들)",     58.3019, -134.4197),

            // PR #25 수정 대상 — OTHER 로 분류돼야 함
            Triple("🇯🇵 Oita (큐슈 — OTHER)",    33.2382, 131.6126),
            Triple("🇯🇵 Fukuoka (큐슈 — OTHER)", 33.5904, 130.4017),
            Triple("🇨🇦 Vancouver (US→OTHER)",   49.2827, -123.1207),
            Triple("🇨🇦 Whitehorse (AK→OTHER)",  60.7212, -135.0568),
            Triple("🇲🇽 Monterrey (US→OTHER)",   25.6866, -100.3161),

            // bbox 한계 — 여전히 US/KR 안에 남지만 NWS 404 + OWM 폴백으로 커버
            Triple("🇨🇦 Toronto (bbox 한계)",    43.6532, -79.3832),
            Triple("🇲🇽 Tijuana (bbox 한계)",    32.5149, -117.0382),

            // OTHER 단독 경로 — OWM 전용
            Triple("🇭🇰 Hong Kong (OTHER)",      22.3193, 114.1694),
        )

        val labels = presets.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.debug_inject_coords_title)
            .setItems(labels) { _, idx ->
                val (label, lat, lon) = presets[idx]
                // GPS 자동감지 OFF → 저장 좌표만 사용. 주입 즉시 바로 반영되도록.
                LocationHelper.setUseGps(this, false)
                LocationHelper.saveLocation(this, LatLon(lat, lon, label))
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.debug_inject_coords_applied, label),
                    android.widget.Toast.LENGTH_LONG
                ).show()
                // 날씨 카드를 즉시 새 좌표로 갱신하도록 refresh 트리거
                b.btnRefreshWeather.performClick()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
                // 로딩 중엔 Region 추측 기반 placeholder. 응답 후 실제 sources 로 교체.
                setupWeatherAttributionByRegion(Region.fromCoordinates(latLon.lat, latLon.lon))

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
                        // 정직한 attribution: Region 이 아니라 **실제로 값을 준 공급자** 기준.
                        // 예) Monterrey(US 박스 안) 에서 NWS 404 → OWM 단독 → sources={OWM}
                        //     → "OpenWeatherMap 제공" 으로 정확히 표시.
                        setupWeatherAttributionBySources(weather.sources)
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
     * 상단 날씨 카드 하단의 출처 표시.
     *
     * **이 함수군은 UI 의 attribution 을 다루는 유일한 경로**이며, 두 형태로 제공:
     *  - [setupWeatherAttributionByRegion]: 공급자 호출 **전** placeholder (로딩 중 공백 방지)
     *  - [setupWeatherAttributionBySources]: 공급자 호출 **후** 진실 (snapshot.sources 기반)
     *
     * 사용자에게 보이는 최종 attribution 은 항상 후자여야 한다. 전자는 잠깐의
     * 로딩 프레임에서만 유효하며, 응답이 오면 즉시 덮어써진다.
     */
    private fun setupWeatherAttributionByRegion(region: Region) {
        // Region 은 "시도할" 공급자 집합을 표현. 최선의 추측이므로 "실제" 와
        // 다를 수 있음을 의식하고 placeholder 로만 사용.
        val guessedSources = when (region) {
            Region.KR    -> setOf(WeatherSource.KMA, WeatherSource.OWM)
            Region.US    -> setOf(WeatherSource.NWS, WeatherSource.OWM)
            Region.OTHER -> setOf(WeatherSource.OWM)
        }
        renderAttribution(guessedSources)
    }

    /**
     * **정직한** attribution — [WeatherSnapshot.sources] 기반.
     *
     * 예) Monterrey(US 박스 안이지만 멕시코 도시):
     *   - Region 라우팅은 US 로 NWS 를 시도하지만
     *   - NWS 가 404 → CrossValidatingWeatherProvider 가 OWM 폴백
     *   - snapshot.sources == {OWM} → UI 는 "OpenWeatherMap 제공" 정확히 표시
     *
     * sources 가 비어있으면 (Worker 시뮬레이션 등 비정상 경로) OWM 로 폴백 —
     * "모름" 을 표시하기보다 가장 범용적인 공급자 라벨이 안전.
     */
    private fun setupWeatherAttributionBySources(sources: Set<WeatherSource>) {
        val effective = sources.ifEmpty { setOf(WeatherSource.OWM) }
        renderAttribution(effective)
    }

    /**
     * [sources] 집합 → 문자열 + ClickableSpan 으로 구성한 후 TextView 주입.
     *
     * 문자열 선택 기준:
     *  - {KMA, OWM}  → "기상청 국가기후데이터센터 + OpenWeatherMap 교차 검증 중"
     *  - {NWS, OWM}  → "NWS + OpenWeatherMap 교차 검증 중"
     *  - {OWM}       → "OpenWeatherMap 제공"
     *  - {KMA}       → "기상청 국가기후데이터센터" (OWM 실패 상황 — 드물지만 있을 수 있음)
     *  - {NWS}       → "NWS"
     *  - {}          → 호출부에서 이미 OWM 으로 폴백했으니 여기 도달 시엔 {OWM}
     *
     * ClickableSpan 은 각 공급자명에 적용 — 약관(공공데이터포털·OWM·NWS)상 출처
     * 링크 요구를 만족. 밑줄·파란색은 제거해 본문과 톤 통일.
     */
    private fun renderAttribution(sources: Set<WeatherSource>) {
        val full = getString(
            when {
                sources.containsAll(listOf(WeatherSource.KMA, WeatherSource.OWM)) -> R.string.weather_attribution_kr
                sources.containsAll(listOf(WeatherSource.NWS, WeatherSource.OWM)) -> R.string.weather_attribution_us
                sources == setOf(WeatherSource.OWM) -> R.string.weather_attribution_global
                sources == setOf(WeatherSource.KMA) -> R.string.weather_attribution_kma
                sources == setOf(WeatherSource.NWS) -> R.string.weather_attribution_nws
                else -> R.string.weather_attribution_global   // 방어 폴백
            }
        )

        val sb = SpannableStringBuilder(full)

        // 기본 ClickableSpan 은 시스템 링크색(파란색) + 밑줄을 강제로 덧씌워 주변 텍스트와
        // 시각적 불일치가 생긴다. 여기선 공급자 표기 자체가 문장의 일부로 읽히길 원하므로
        // updateDrawState 로 링크 스타일을 제거.
        fun linkSpan(url: String) = object : ClickableSpan() {
            override fun onClick(widget: View) { openUrl(url) }
            override fun updateDrawState(ds: android.text.TextPaint) {
                ds.isUnderlineText = false
            }
        }

        // 실제 sources 에 포함된 공급자만 링크 span 적용 — 약관 의무 이행.
        val spans: List<Pair<String, String>> = buildList {
            if (WeatherSource.KMA in sources) add(getString(R.string.weather_attribution_kma) to "https://data.kma.go.kr")
            if (WeatherSource.NWS in sources) add(getString(R.string.weather_attribution_nws) to "https://www.weather.gov")
            if (WeatherSource.OWM in sources) add(getString(R.string.weather_attribution_owm) to "https://openweathermap.org")
        }
        spans.forEach { (name, url) ->
            val start = full.indexOf(name)
            if (start >= 0) {
                sb.setSpan(linkSpan(url), start, start + name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
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
