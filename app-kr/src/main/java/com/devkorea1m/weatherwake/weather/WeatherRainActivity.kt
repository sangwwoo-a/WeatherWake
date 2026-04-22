package com.devkorea1m.weatherwake.weather

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.devkorea1m.weatherwake.R
import com.devkorea1m.weatherwake.databinding.ActivityWeatherRainBinding
import com.devkorea1m.weatherwake.domain.Region
import com.devkorea1m.weatherwake.sound.SoundPickerActivity
import com.devkorea1m.weatherwake.util.AlarmConstants
import com.devkorea1m.weatherwake.util.currentRegion

/**
 * 2단계: 비 앞당김 시간 + 민감도 선택
 *
 * 진입: AlarmSettingActivity (날씨 연동 ON)
 * 진출: WeatherSnowActivity → SoundPickerActivity 체인
 * 반환: SoundPickerActivity.EXTRA_SOUND_URI/NAME + EXTRA_RAIN/SNOW_ADVANCE/SENSITIVITY
 */
class WeatherRainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RAIN_ADVANCE     = "rain_advance_min"
        const val EXTRA_SNOW_ADVANCE     = "snow_advance_min"
        const val EXTRA_RAIN_SENSITIVITY = "rain_sensitivity"
        const val EXTRA_SNOW_SENSITIVITY = "snow_sensitivity"
    }

    private lateinit var b: ActivityWeatherRainBinding

    // 선택된 민감도 (0=아주민감 1=민감 2=보통 3=둔감)
    private var selectedSensitivity  = 2
    // 스피너 선택값 캐싱 — 콜백/재생성 시 b.spinner 직접 접근 방지
    private var selectedAdvanceIndex = 2   // 기본값 30분 (index 2)

    private val snowLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // 체인 끝 결과를 그대로 위로 전달
                setResult(Activity.RESULT_OK, result.data)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityWeatherRainBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(b.toolbarRain)
        b.toolbarRain.setNavigationOnClickListener { finish() }

        // 스피너 초기화 — 공통 상수 사용
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, AlarmConstants.getAdvanceLabelsFull(this))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        b.spinnerRainAdvance.adapter = adapter

        // 이전 화면에서 받은 값 복원
        val initAdvance     = intent.getIntExtra(EXTRA_RAIN_ADVANCE, 30)
        val initSensitivity = intent.getIntExtra(EXTRA_RAIN_SENSITIVITY, 2)
        selectedAdvanceIndex = AlarmConstants.ADVANCE_OPTIONS.indexOf(initAdvance).coerceAtLeast(0)
        b.spinnerRainAdvance.setSelection(selectedAdvanceIndex)

        // 스피너 선택 변경 시 캐시 업데이트
        b.spinnerRainAdvance.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                selectedAdvanceIndex = pos
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        setSensitivity(initSensitivity)

        // 카드 클릭
        b.cardRainVerySensitive.setOnClickListener { setSensitivity(0) }
        b.cardRainSensitive.setOnClickListener     { setSensitivity(1) }
        b.cardRainNormal.setOnClickListener        { setSensitivity(2) }
        b.cardRainDull.setOnClickListener          { setSensitivity(3) }

        b.btnRainBack.setOnClickListener { finish() }
        b.btnRainNext.setOnClickListener { goToSnow() }

        // 지역 기반 하단 공급자 안내 문구 주입 — KR→KMA, US→NWS, 그 외→OWM 단독
        b.tvWeatherInfo.setText(weatherInfoResFor(currentRegion(this)))
    }

    private fun weatherInfoResFor(region: Region): Int = when (region) {
        Region.KR    -> R.string.label_weather_info_kr
        Region.US    -> R.string.label_weather_info_us
        Region.OTHER -> R.string.label_weather_info_global
    }

    private fun setSensitivity(level: Int) {
        selectedSensitivity = level
        val selectedColor = ContextCompat.getColor(this, R.color.primary_blue)
        val defaultColor  = ContextCompat.getColor(this, R.color.outline)

        b.cardRainVerySensitive.strokeColor = if (level == 0) selectedColor else defaultColor
        b.cardRainSensitive.strokeColor     = if (level == 1) selectedColor else defaultColor
        b.cardRainNormal.strokeColor        = if (level == 2) selectedColor else defaultColor
        b.cardRainDull.strokeColor          = if (level == 3) selectedColor else defaultColor

        b.radioRainVerySensitive.isChecked = level == 0
        b.radioRainSensitive.isChecked     = level == 1
        b.radioRainNormal.isChecked        = level == 2
        b.radioRainDull.isChecked          = level == 3
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("rain_advance_index", selectedAdvanceIndex)
        outState.putInt("rain_sensitivity",   selectedSensitivity)
    }

    override fun onRestoreInstanceState(savedState: Bundle) {
        super.onRestoreInstanceState(savedState)
        selectedAdvanceIndex = savedState.getInt("rain_advance_index", selectedAdvanceIndex)
        selectedSensitivity  = savedState.getInt("rain_sensitivity",   selectedSensitivity)
        b.spinnerRainAdvance.setSelection(selectedAdvanceIndex)
        setSensitivity(selectedSensitivity)
    }

    private fun goToSnow() {
        val rainAdvanceMin = AlarmConstants.ADVANCE_OPTIONS
            .getOrElse(selectedAdvanceIndex) { AlarmConstants.DEFAULT_RAIN_ADVANCE_MIN }
        snowLauncher.launch(
            Intent(this, WeatherSnowActivity::class.java).apply {
                putExtra(EXTRA_RAIN_ADVANCE,     rainAdvanceMin)
                putExtra(EXTRA_SNOW_ADVANCE,     intent.getIntExtra(EXTRA_SNOW_ADVANCE, 60))
                putExtra(EXTRA_RAIN_SENSITIVITY, selectedSensitivity)
                putExtra(EXTRA_SNOW_SENSITIVITY, intent.getIntExtra(EXTRA_SNOW_SENSITIVITY, 2))
                // 소리 정보도 체인으로 전달
                putExtra(SoundPickerActivity.EXTRA_SOUND_URI,  intent.getStringExtra(SoundPickerActivity.EXTRA_SOUND_URI)  ?: "")
                putExtra(SoundPickerActivity.EXTRA_SOUND_NAME, intent.getStringExtra(SoundPickerActivity.EXTRA_SOUND_NAME) ?: "기본 알람음")
            }
        )
    }
}
