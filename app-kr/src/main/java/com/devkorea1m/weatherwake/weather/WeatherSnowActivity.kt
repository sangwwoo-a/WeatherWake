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
import com.devkorea1m.weatherwake.databinding.ActivityWeatherSnowBinding
import com.devkorea1m.weatherwake.domain.Region
import com.devkorea1m.weatherwake.sound.SoundPickerActivity
import com.devkorea1m.weatherwake.util.AlarmConstants
import com.devkorea1m.weatherwake.util.currentRegion

/**
 * 3단계: 눈 앞당김 시간 + 민감도 선택
 *
 * 진입: WeatherRainActivity
 * 진출: SoundPickerActivity
 * 반환: SoundPickerActivity 결과 + 모든 날씨 설정값을 위로 전달
 */
class WeatherSnowActivity : AppCompatActivity() {

    private lateinit var b: ActivityWeatherSnowBinding

    private var selectedSensitivity   = 2
    // 스피너 선택값을 멤버 변수로 캐싱 — SoundPicker에서 돌아오는 콜백 시점에
    // Activity가 재생성될 수 있으므로 b.spinner 직접 접근을 피함
    private var selectedAdvanceIndex  = 4   // 기본값 60분 (index 4)

    private val soundPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // 캐싱된 멤버 변수 사용 (b.spinner 접근 없음)
                val snowAdvanceMin = AlarmConstants.ADVANCE_OPTIONS
                    .getOrElse(selectedAdvanceIndex) { AlarmConstants.DEFAULT_SNOW_ADVANCE_MIN }

                val resultIntent = Intent().apply {
                    putExtra(SoundPickerActivity.EXTRA_SOUND_URI,        result.data?.getStringExtra(SoundPickerActivity.EXTRA_SOUND_URI)  ?: "")
                    putExtra(SoundPickerActivity.EXTRA_SOUND_NAME,       result.data?.getStringExtra(SoundPickerActivity.EXTRA_SOUND_NAME) ?: "기본 알람음")
                    putExtra(WeatherRainActivity.EXTRA_RAIN_ADVANCE,     intent.getIntExtra(WeatherRainActivity.EXTRA_RAIN_ADVANCE, 30))
                    putExtra(WeatherRainActivity.EXTRA_RAIN_SENSITIVITY, intent.getIntExtra(WeatherRainActivity.EXTRA_RAIN_SENSITIVITY, 2))
                    putExtra(WeatherRainActivity.EXTRA_SNOW_ADVANCE,     snowAdvanceMin)
                    putExtra(WeatherRainActivity.EXTRA_SNOW_SENSITIVITY, selectedSensitivity)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityWeatherSnowBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(b.toolbarSnow)
        b.toolbarSnow.setNavigationOnClickListener { finish() }

        // 스피너 초기화 — 공통 상수 사용
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, AlarmConstants.getAdvanceLabelsFull(this))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        b.spinnerSnowAdvance.adapter = adapter

        val initAdvance     = intent.getIntExtra(WeatherRainActivity.EXTRA_SNOW_ADVANCE, AlarmConstants.DEFAULT_SNOW_ADVANCE_MIN)
        val initSensitivity = intent.getIntExtra(WeatherRainActivity.EXTRA_SNOW_SENSITIVITY, 2)

        selectedAdvanceIndex = AlarmConstants.ADVANCE_OPTIONS.indexOf(initAdvance).coerceAtLeast(0)
        b.spinnerSnowAdvance.setSelection(selectedAdvanceIndex)

        // 스피너 선택 변경 시 캐시 업데이트
        b.spinnerSnowAdvance.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                selectedAdvanceIndex = pos
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        setSensitivity(initSensitivity)

        b.cardSnowVerySensitive.setOnClickListener { setSensitivity(0) }
        b.cardSnowSensitive.setOnClickListener     { setSensitivity(1) }
        b.cardSnowNormal.setOnClickListener        { setSensitivity(2) }
        b.cardSnowDull.setOnClickListener          { setSensitivity(3) }

        b.btnSnowBack.setOnClickListener { finish() }
        b.btnSnowNext.setOnClickListener { goToSoundPicker() }

        // 지역 기반 하단 공급자 안내 문구 주입
        b.tvWeatherInfo.setText(weatherInfoResFor(currentRegion(this)))
    }

    private fun weatherInfoResFor(region: Region): Int = when (region) {
        Region.KR    -> R.string.label_weather_info_kr
        Region.US    -> R.string.label_weather_info_us
        Region.OTHER -> R.string.label_weather_info_global
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("snow_advance_index", selectedAdvanceIndex)
        outState.putInt("snow_sensitivity",   selectedSensitivity)
    }

    override fun onRestoreInstanceState(savedState: Bundle) {
        super.onRestoreInstanceState(savedState)
        selectedAdvanceIndex = savedState.getInt("snow_advance_index", selectedAdvanceIndex)
        selectedSensitivity  = savedState.getInt("snow_sensitivity",   selectedSensitivity)
        b.spinnerSnowAdvance.setSelection(selectedAdvanceIndex)
        setSensitivity(selectedSensitivity)
    }

    private fun setSensitivity(level: Int) {
        selectedSensitivity = level
        val selectedColor = ContextCompat.getColor(this, R.color.primary_blue)
        val defaultColor  = ContextCompat.getColor(this, R.color.outline)

        b.cardSnowVerySensitive.strokeColor = if (level == 0) selectedColor else defaultColor
        b.cardSnowSensitive.strokeColor     = if (level == 1) selectedColor else defaultColor
        b.cardSnowNormal.strokeColor        = if (level == 2) selectedColor else defaultColor
        b.cardSnowDull.strokeColor          = if (level == 3) selectedColor else defaultColor

        b.radioSnowVerySensitive.isChecked = level == 0
        b.radioSnowSensitive.isChecked     = level == 1
        b.radioSnowNormal.isChecked        = level == 2
        b.radioSnowDull.isChecked          = level == 3
    }

    private fun goToSoundPicker() {
        soundPickerLauncher.launch(
            Intent(this, SoundPickerActivity::class.java).apply {
                putExtra(SoundPickerActivity.EXTRA_SOUND_URI,  intent.getStringExtra(SoundPickerActivity.EXTRA_SOUND_URI)  ?: "")
                putExtra(SoundPickerActivity.EXTRA_SOUND_NAME, intent.getStringExtra(SoundPickerActivity.EXTRA_SOUND_NAME) ?: "기본 알람음")
            }
        )
    }
}
