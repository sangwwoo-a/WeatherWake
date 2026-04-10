package com.devkorea1m.weatherwake

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.devkorea1m.weatherwake.data.model.AlarmEntity
import com.devkorea1m.weatherwake.databinding.ActivityAlarmSettingBinding
import com.devkorea1m.weatherwake.sound.SoundPickerActivity
import com.devkorea1m.weatherwake.viewmodel.AlarmViewModel

class AlarmSettingActivity : AppCompatActivity() {

    private lateinit var b: ActivityAlarmSettingBinding
    private val viewModel: AlarmViewModel by viewModels()
    private var editAlarmId: Int = -1

    // 앞당김 옵션
    private val advanceOptions = listOf(0, 15, 30, 45, 60)
    private val advanceLabels  = listOf("없음", "15분", "30분", "45분", "1시간")

    // SoundPickerActivity 결과 처리: 소리 선택 완료 시 알람 저장
    private val soundPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val soundUri  = result.data?.getStringExtra(SoundPickerActivity.EXTRA_SOUND_URI)  ?: ""
                val soundName = result.data?.getStringExtra(SoundPickerActivity.EXTRA_SOUND_NAME) ?: "기본 알람음"
                saveAlarm(soundUri, soundName)
            }
            // RESULT_CANCELED: 사용자가 뒤로 눌러 취소 — 아무것도 하지 않음
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAlarmSettingBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbarSetting)
        b.toolbarSetting.setNavigationOnClickListener { finish() }

        editAlarmId = intent.getIntExtra("alarm_id", -1)

        setupAdvancePickers()

        // 편집 모드: 기존 알람 데이터 불러오기
        if (editAlarmId != -1) {
            viewModel.alarms.observe(this) { alarms ->
                alarms.find { it.id == editAlarmId }?.let { populate(it) }
            }
        }

        // "다음 →" 버튼: 알람음 선택 화면으로 이동
        b.btnSave.setOnClickListener { goToSoundPicker() }
        b.btnCancel.setOnClickListener { finish() }
    }

    private fun setupAdvancePickers() {
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, advanceLabels)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        b.spinnerRainAdvance.adapter = adapter
        b.spinnerSnowAdvance.adapter = adapter
        b.spinnerRainAdvance.setSelection(2)   // 기본 30분
        b.spinnerSnowAdvance.setSelection(4)   // 기본 1시간
    }

    private fun populate(alarm: AlarmEntity) {
        b.timePicker.hour   = alarm.hour
        b.timePicker.minute = alarm.minute
        b.etLabel.setText(alarm.label)
        b.switchWeather.isChecked = alarm.weatherTrigger
        b.spinnerRainAdvance.setSelection(advanceOptions.indexOf(alarm.rainAdvanceMin).coerceAtLeast(0))
        b.spinnerSnowAdvance.setSelection(advanceOptions.indexOf(alarm.snowAdvanceMin).coerceAtLeast(0))
        // 기존 알람음 정보를 SoundPickerActivity에 넘기기 위해 인텐트 extras로 전달
        pendingSoundUri  = alarm.soundUri
        pendingSoundName = alarm.soundName
    }

    // Step 1에서 수집한 값들을 임시 보관 (SoundPicker로 넘길 때 사용)
    private var pendingSoundUri  = ""
    private var pendingSoundName = "기본 알람음"

    private fun goToSoundPicker() {
        soundPickerLauncher.launch(
            Intent(this, SoundPickerActivity::class.java).apply {
                // 현재 step 1 폼 데이터를 같이 전달 (저장은 step 2 완료 후)
                putExtra(SoundPickerActivity.EXTRA_SOUND_URI,  pendingSoundUri)
                putExtra(SoundPickerActivity.EXTRA_SOUND_NAME, pendingSoundName)
                // 알람 메타데이터도 함께 전달 (SoundPicker가 저장 책임 X, 여기서 저장)
            }
        )
    }

    private fun saveAlarm(soundUri: String, soundName: String) {
        val alarm = AlarmEntity(
            id             = if (editAlarmId != -1) editAlarmId else 0,
            hour           = b.timePicker.hour,
            minute         = b.timePicker.minute,
            label          = b.etLabel.text.toString().ifBlank { "알람" },
            weatherTrigger = b.switchWeather.isChecked,
            rainAdvanceMin = advanceOptions[b.spinnerRainAdvance.selectedItemPosition],
            snowAdvanceMin = advanceOptions[b.spinnerSnowAdvance.selectedItemPosition],
            soundUri       = soundUri,
            soundName      = soundName
        )

        if (editAlarmId != -1) viewModel.updateAlarm(alarm)
        else viewModel.addAlarm(alarm)

        finish()
    }
}
