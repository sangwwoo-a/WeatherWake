package com.devkorea1m.weatherwake

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.devkorea1m.weatherwake.BuildConfig
import com.devkorea1m.weatherwake.data.model.WeatherConditionType
import com.devkorea1m.weatherwake.data.repository.WeatherRepository
import com.devkorea1m.weatherwake.databinding.ActivityMainBinding
import com.devkorea1m.weatherwake.util.LocationHelper
import com.devkorea1m.weatherwake.viewmodel.AlarmViewModel
import com.devkorea1m.weatherwake.worker.WeatherCheckWorker
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
                .setMessage("'${alarm.label}' 알람을 삭제할까요?")
                .setPositiveButton("삭제") { _, _ -> viewModel.deleteAlarm(alarm) }
                .setNegativeButton("취소", null)
                .show()
        }
    )

    // 위치 권한 요청 런처
    private val locationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                refreshWeatherCard()
            } else {
                // 권한 거부 — 저장된 위치로 대체
                refreshWeatherCard()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)

        b.rvAlarms.adapter = adapter
        viewModel.alarms.observe(this) { adapter.submitList(it) }

        b.fabAdd.setOnClickListener {
            startActivity(Intent(this, AlarmSettingActivity::class.java))
        }

        b.btnLocation.setOnClickListener {
            startActivity(Intent(this, LocationSettingActivity::class.java))
        }

        // 위치 권한 확인 후 날씨 카드 갱신
        ensureLocationPermission()

        // WorkManager 30분 주기 시작
        WeatherCheckWorker.enqueue(this)
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) refreshWeatherCard()
    }

    // ─── 권한 헬퍼 ───────────────────────────────────────

    private fun weatherEmoji(type: WeatherConditionType) = when (type) {
        WeatherConditionType.RAIN  -> "☔"
        WeatherConditionType.SNOW  -> "❄️"
        WeatherConditionType.CLEAR -> "☀️"
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun ensureLocationPermission() {
        if (hasLocationPermission()) {
            refreshWeatherCard()
        } else {
            locationPermLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
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
                    b.tvWeatherStatus.text = "위치를 설정해 주세요"
                    b.tvWeatherCity.text   = ""
                    b.tvWeatherTemp.text   = ""
                    return@runCatching
                }

                b.tvWeatherCity.text   = "📍 ${latLon.label}"
                b.tvWeatherStatus.text = "날씨 확인 중…"

                val weather = WeatherRepository().getCurrentWeather(
                    lat    = latLon.lat,
                    lon    = latLon.lon,
                    apiKey = BuildConfig.OWM_API_KEY
                )
                if (weather != null) {
                    b.tvWeatherStatus.text = weather.description
                    b.tvWeatherTemp.text   = "%.1f°C".format(weather.tempCelsius)
                    b.tvWeatherCity.text   = "📍 ${weather.cityName}"
                    b.tvWeatherEmoji.text  = weatherEmoji(weather.conditionType)
                } else {
                    b.tvWeatherStatus.text = "날씨 정보를 불러올 수 없어요"
                    b.tvWeatherTemp.text   = ""
                    b.tvWeatherEmoji.text  = "🌡"
                }
            }.onFailure { e ->
                // 어떤 예외도 앱을 죽이지 않도록 방어
                e.printStackTrace()
                runCatching {
                    b.tvWeatherStatus.text = "오류가 발생했어요"
                    b.tvWeatherTemp.text   = ""
                }
            }
        }
    }
}
