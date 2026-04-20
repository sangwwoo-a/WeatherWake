package com.devkorea1m.weatherwake

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.devkorea1m.weatherwake.data.model.AlarmEntity
import com.devkorea1m.weatherwake.databinding.ActivityAlarmSettingBinding
import com.devkorea1m.weatherwake.sound.SoundPickerActivity
import com.devkorea1m.weatherwake.viewmodel.AlarmViewModel
import com.devkorea1m.weatherwake.weather.WeatherRainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmSettingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }

    private lateinit var b: ActivityAlarmSettingBinding
    private val viewModel: AlarmViewModel by viewModels()
    private var editAlarmId: Int = -1

    // 날씨 설정 화면에서 돌아올 때 결과 수신 (소리 선택까지 완료 후 저장)
    private val weatherRainLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val soundUri        = result.data?.getStringExtra(SoundPickerActivity.EXTRA_SOUND_URI)       ?: ""
                val soundName       = result.data?.getStringExtra(SoundPickerActivity.EXTRA_SOUND_NAME)      ?: getString(R.string.label_default_alarm_label)
                val rainAdvance     = result.data?.getIntExtra(WeatherRainActivity.EXTRA_RAIN_ADVANCE,     30) ?: 30
                val snowAdvance     = result.data?.getIntExtra(WeatherRainActivity.EXTRA_SNOW_ADVANCE,     60) ?: 60
                val rainSensitivity = result.data?.getIntExtra(WeatherRainActivity.EXTRA_RAIN_SENSITIVITY, 2)  ?: 2
                val snowSensitivity = result.data?.getIntExtra(WeatherRainActivity.EXTRA_SNOW_SENSITIVITY, 2)  ?: 2
                saveAlarm(soundUri, soundName, rainAdvance, snowAdvance, rainSensitivity, snowSensitivity)
            }
        }

    // 날씨 연동 OFF일 때: SoundPicker로 바로 이동
    private val soundPickerDirectLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val soundUri  = result.data?.getStringExtra(SoundPickerActivity.EXTRA_SOUND_URI)  ?: ""
                val soundName = result.data?.getStringExtra(SoundPickerActivity.EXTRA_SOUND_NAME) ?: getString(R.string.label_default_alarm_label)
                saveAlarm(soundUri, soundName,
                    pendingRainAdvance, pendingSnowAdvance,
                    pendingRainSensitivity, pendingSnowSensitivity)
            }
        }

    // 편집 모드에서 기존 날씨/소리 설정 임시 보관
    private var pendingSoundUri        = ""
    private var pendingSoundName       = ""
    private var pendingRainAdvance     = 30
    private var pendingSnowAdvance     = 60
    private var pendingRainSensitivity = 2
    private var pendingSnowSensitivity = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityAlarmSettingBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(b.toolbarSetting)
        b.toolbarSetting.setNavigationOnClickListener { finish() }

        editAlarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)

        setDayChips(0b0111110)   // 신규 알람 기본값: 주중(월~금)만 선택
        applyDayChipColors()
        setupNoRepeatChip()

        // 날씨 스위치 → 버튼 텍스트 토글
        b.switchWeather.setOnCheckedChangeListener { _, isChecked ->
            b.btnSave.text = if (isChecked) getString(R.string.action_weather_setup) else getString(R.string.action_sound_setup)
        }
        b.btnSave.text = getString(R.string.action_weather_setup)  // 기본값 ON

        // 편집 모드: 기존 알람 데이터 불러오기
        if (editAlarmId != -1) {
            viewModel.alarms.observe(this) { alarms ->
                alarms.find { it.id == editAlarmId }?.let { populate(it) }
            }
        }

        b.btnSave.setOnClickListener {
            if (b.switchWeather.isChecked) goToWeatherRain()
            else goToSoundPickerDirect()
        }
        b.btnCancel.setOnClickListener { finish() }
    }

    private fun populate(alarm: AlarmEntity) {
        b.timePicker.hour   = alarm.hour
        b.timePicker.minute = alarm.minute
        b.etLabel.setText(alarm.label)
        b.switchWeather.isChecked = alarm.weatherTrigger
        b.btnSave.text = if (alarm.weatherTrigger) getString(R.string.action_weather_setup) else getString(R.string.action_sound_setup)
        setDayChips(alarm.repeatDays)

        pendingSoundUri        = alarm.soundUri
        pendingSoundName       = alarm.soundName
        pendingRainAdvance     = alarm.rainAdvanceMin
        pendingSnowAdvance     = alarm.snowAdvanceMin
        pendingRainSensitivity = alarm.rainSensitivity
        pendingSnowSensitivity = alarm.snowSensitivity
    }

    // ─── 요일 칩 ────────────────────────────────────────────────

    private fun setDayChips(mask: Int) {
        val chips = listOf(b.chipSun, b.chipMon, b.chipTue, b.chipWed, b.chipThu, b.chipFri, b.chipSat)
        if (mask == 0) {
            chips.forEach { it.isChecked = false; it.isEnabled = false }
            b.chipNoRepeat.isChecked = true
        } else {
            chips.forEachIndexed { i, chip ->
                chip.isEnabled = true
                chip.isChecked = (mask and (1 shl i)) != 0
            }
            b.chipNoRepeat.isChecked = false
        }
    }

    private fun setupNoRepeatChip() {
        val chips = listOf(b.chipSun, b.chipMon, b.chipTue, b.chipWed, b.chipThu, b.chipFri, b.chipSat)
        b.chipNoRepeat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) chips.forEach { it.isChecked = false; it.isEnabled = false }
            else           chips.forEach { it.isEnabled = true }
        }
        chips.forEach { chip ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) b.chipNoRepeat.isChecked = false
            }
        }
    }

    private fun applyDayChipColors() {
        val red   = Color.parseColor("#E53935")
        val blue  = Color.parseColor("#1E88E5")
        val white = getColor(R.color.white)
        b.chipSun.setTextColor(ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(white, red)
        ))
        b.chipSat.setTextColor(ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(white, blue)
        ))
    }

    private fun getRepeatDays(): Int {
        if (b.chipNoRepeat.isChecked) return 0
        val chips = listOf(b.chipSun, b.chipMon, b.chipTue, b.chipWed, b.chipThu, b.chipFri, b.chipSat)
        val mask = chips.foldIndexed(0) { i, acc, chip -> if (chip.isChecked) acc or (1 shl i) else acc }
        return if (mask == 0) 0b1111111 else mask
    }

    // ─── 화면 이동 ───────────────────────────────────────────────

    private fun goToWeatherRain() {
        weatherRainLauncher.launch(
            Intent(this, WeatherRainActivity::class.java).apply {
                putExtra(WeatherRainActivity.EXTRA_RAIN_ADVANCE,     pendingRainAdvance)
                putExtra(WeatherRainActivity.EXTRA_SNOW_ADVANCE,     pendingSnowAdvance)
                putExtra(WeatherRainActivity.EXTRA_RAIN_SENSITIVITY, pendingRainSensitivity)
                putExtra(WeatherRainActivity.EXTRA_SNOW_SENSITIVITY, pendingSnowSensitivity)
                putExtra(SoundPickerActivity.EXTRA_SOUND_URI,        pendingSoundUri)
                putExtra(SoundPickerActivity.EXTRA_SOUND_NAME,       pendingSoundName)
            }
        )
    }

    private fun goToSoundPickerDirect() {
        soundPickerDirectLauncher.launch(
            Intent(this, SoundPickerActivity::class.java).apply {
                putExtra(SoundPickerActivity.EXTRA_SOUND_URI,  pendingSoundUri)
                putExtra(SoundPickerActivity.EXTRA_SOUND_NAME, pendingSoundName)
            }
        )
    }

    private fun saveAlarm(
        soundUri: String, soundName: String,
        rainAdvanceMin: Int, snowAdvanceMin: Int,
        rainSensitivity: Int, snowSensitivity: Int
    ) {
        val alarm = AlarmEntity(
            id              = if (editAlarmId != -1) editAlarmId else 0,
            hour            = b.timePicker.hour,
            minute          = b.timePicker.minute,
            repeatDays      = getRepeatDays(),
            label           = b.etLabel.text.toString().ifBlank { getString(R.string.label_default_alarm_label) },
            weatherTrigger  = b.switchWeather.isChecked,
            rainAdvanceMin  = rainAdvanceMin,
            snowAdvanceMin  = snowAdvanceMin,
            soundUri        = soundUri,
            soundName       = soundName,
            rainSensitivity = rainSensitivity,
            snowSensitivity = snowSensitivity
        )
        // 날씨 연동 알람은 저장 직전에 작동 방식 안내 다이얼로그를 띄워 명시적 동의를 받는다.
        // 법적 고지 + 사용자 인지 확인 역할.
        if (alarm.weatherTrigger) {
            showWeatherNoticeDialog(alarm)
        } else {
            persistAlarm(alarm)
        }
    }

    private fun showWeatherNoticeDialog(alarm: AlarmEntity) {
        val checkTime        = formatTimeOfDay(alarm.hour, alarm.minute, minusMinutes = 90)
        val alarmTime        = formatTimeOfDay(alarm.hour, alarm.minute, minusMinutes = 0)
        val rainAdvancedTime = formatTimeOfDay(alarm.hour, alarm.minute, minusMinutes = alarm.rainAdvanceMin)
        val snowAdvancedTime = formatTimeOfDay(alarm.hour, alarm.minute, minusMinutes = alarm.snowAdvanceMin)
        val repeatLabel      = formatRepeatLabel(alarm.repeatDays)
        val body = getString(
            R.string.dialog_weather_notice_body,
            checkTime,              // %1$s — 날씨 체크 시점 (알람 90분 전)
            alarmTime,              // %2$s — 알람 발동 시각 (원래 시각)
            rainAdvancedTime,       // %3$s — 비 감지 시 울릴 시각
            snowAdvancedTime,       // %4$s — 눈 감지 시 울릴 시각
            alarm.rainAdvanceMin,   // %5$d — 비 앞당김 분
            alarm.snowAdvanceMin,   // %6$d — 눈 앞당김 분
            repeatLabel,            // %7$s — 반복 패턴 ("매일"/"평일(월~금)"/"월·수·금"/"한 번만" 등)
            alarm.label             // %8$s — 알람명
        )

        // 커스텀 레이아웃으로 제목/본문 색 반전 + 경고 섹션 노란색 표시
        val view = layoutInflater.inflate(R.layout.dialog_weather_notice, null)
        view.findViewById<android.widget.TextView>(R.id.tvNoticeTitle)
            .setText(R.string.dialog_weather_notice_title)
        view.findViewById<android.widget.TextView>(R.id.tvNoticeBody)
            .text = highlightLabelOccurrences(body, alarm.label)
        view.findViewById<android.widget.TextView>(R.id.tvNoticeWarning)
            .setText(R.string.dialog_weather_notice_warning)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setCancelable(false)
            .setNeutralButton(R.string.dialog_weather_notice_learn_more, null)  // 수동 핸들링 (자동 dismiss 방지)
            .setPositiveButton(R.string.dialog_weather_notice_ok) { _, _ ->
                persistAlarm(alarm)
            }
            .create()

        dialog.setOnShowListener {
            // "자세히 알아보기" 버튼은 상세 다이얼로그를 열되, 이 메인 다이얼로그는 유지.
            // MaterialAlertDialog 는 기본적으로 버튼 클릭 시 자동 dismiss 되지만,
            // show() 이후 직접 리스너를 덮어쓰면 자동 dismiss 가 비활성화됨.
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                showAdvanceDetailDialog(alarm)
            }
        }

        dialog.show()
    }

    /**
     * "자세히 알아보기" 탭 시 열리는 상세 다이얼로그.
     * 조회 결과에 따른 3가지 시나리오(비/눈/맑음 각각의 실제 울림 시각) + 90분 윈도우 한계 안내.
     */
    private fun showAdvanceDetailDialog(alarm: AlarmEntity) {
        val checkTime        = formatTimeOfDay(alarm.hour, alarm.minute, minusMinutes = 90)
        val alarmTime        = formatTimeOfDay(alarm.hour, alarm.minute, minusMinutes = 0)
        val rainAdvancedTime = formatTimeOfDay(alarm.hour, alarm.minute, minusMinutes = alarm.rainAdvanceMin)
        val snowAdvancedTime = formatTimeOfDay(alarm.hour, alarm.minute, minusMinutes = alarm.snowAdvanceMin)
        val detailBody = getString(
            R.string.dialog_weather_notice_detail_body,
            checkTime,              // %1$s
            alarmTime,              // %2$s
            rainAdvancedTime,       // %3$s
            snowAdvancedTime,       // %4$s
            alarm.rainAdvanceMin,   // %5$d
            alarm.snowAdvanceMin,   // %6$d
            formatRepeatLabel(alarm.repeatDays),  // %7$s
            alarm.label             // %8$s
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_weather_notice_detail_title)
            .setMessage(highlightLabelOccurrences(detailBody, alarm.label))
            .setPositiveButton(R.string.dialog_weather_notice_detail_close, null)
            .show()
    }

    private fun persistAlarm(alarm: AlarmEntity) {
        if (editAlarmId != -1) viewModel.updateAlarm(alarm)
        else viewModel.addAlarm(alarm)
        finish()
    }

    /**
     * 다이얼로그 본문에서 알람명(label) 위치를 primary_blue 로 강조.
     * 문자열 리소스에서 %8$s 앞뒤로 \u200B(zero-width space) 마커를 씌워놨기 때문에,
     * 마커 페어 사이의 구간만 색칠하면 "알람" 같은 공통 단어가 label 로 설정돼도
     * 본문 내 다른 "알람" 들은 건드리지 않는다.
     */
    private fun highlightLabelOccurrences(body: String, label: String): CharSequence {
        val spannable = android.text.SpannableStringBuilder(body)
        if (label.isBlank()) return spannable
        val color = androidx.core.content.ContextCompat.getColor(this, R.color.primary_blue)
        val marker = '\u200B'
        var i = 0
        while (i < body.length) {
            val start = body.indexOf(marker, i)
            if (start < 0) break
            val end = body.indexOf(marker, start + 1)
            if (end < 0) break
            // [start+1 .. end) 구간이 실제 label 본문. 마커 자체는 invisible 이라 남겨둬도 무방.
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(color),
                start + 1, end,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            i = end + 1
        }
        return spannable
    }

    /**
     * repeatDays 비트마스크를 사용자 친화적 라벨로 변환.
     *   0                 → "한 번만" / "(one-time)"
     *   0b1111111 (127)   → "매일" / "every day"
     *   0b0111110 (62)    → "평일(월~금)" / "on weekdays"
     *   0b1000001 (65)    → "주말(토·일)" / "on weekends"
     *   기타              → 선택 요일을 "·"로 연결 (예: "월·수·금")
     */
    private fun formatRepeatLabel(repeatDays: Int): String = when (repeatDays) {
        0         -> getString(R.string.dialog_repeat_once)
        0b1111111 -> getString(R.string.dialog_repeat_every_day)
        0b0111110 -> getString(R.string.dialog_repeat_weekdays)
        0b1000001 -> getString(R.string.dialog_repeat_weekends)
        else -> {
            // bit0=일, bit1=월, ..., bit6=토
            val dayNames = listOf(
                getString(R.string.day_sun),
                getString(R.string.day_mon),
                getString(R.string.day_tue),
                getString(R.string.day_wed),
                getString(R.string.day_thu),
                getString(R.string.day_fri),
                getString(R.string.day_sat)
            )
            dayNames
                .filterIndexed { i, _ -> (repeatDays and (1 shl i)) != 0 }
                .joinToString("·")
        }
    }

    /**
     * "알람 시각 - minusMinutes" 의 시각을 "오전/오후 H:MM" 형식으로 반환.
     * 자정 걸쳐 전날로 넘어가는 경우에도 시각만 표시 (예: 00:30 알람 - 90 → "오후 11:00").
     */
    private fun formatTimeOfDay(hour: Int, minute: Int, minusMinutes: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -minusMinutes)
        }
        return SimpleDateFormat("a h:mm", Locale.getDefault()).format(cal.time)
    }
}
