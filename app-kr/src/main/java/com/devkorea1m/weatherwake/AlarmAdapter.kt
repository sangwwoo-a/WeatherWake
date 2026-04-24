package com.devkorea1m.weatherwake

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.devkorea1m.weatherwake.data.model.AlarmEntity
import com.devkorea1m.weatherwake.databinding.ItemAlarmBinding

class AlarmAdapter(
    private val onToggle: (AlarmEntity) -> Unit,
    private val onWeatherToggle: (AlarmEntity) -> Unit,
    private val onEdit: (AlarmEntity) -> Unit,
    private val onDelete: (AlarmEntity) -> Unit,
    // 선택 모드 상태가 바뀔 때마다 호출 (활성 여부, 선택된 수)
    private val onSelectionChanged: (isActive: Boolean, count: Int) -> Unit
) : ListAdapter<AlarmEntity, AlarmAdapter.ViewHolder>(DIFF) {

    // ── 선택 상태 ─────────────────────────────────────────────
    private val selectedIds = mutableSetOf<Int>()
    var isSelectionMode = false
        private set

    /** 현재 선택된 알람 ID 목록 */
    fun getSelectedIds(): Set<Int> = selectedIds.toSet()

    /** 선택 모드 해제 및 선택 초기화 */
    fun clearSelection() {
        selectedIds.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(false, 0)
    }

    // ── ViewHolder ────────────────────────────────────────────
    inner class ViewHolder(private val b: ItemAlarmBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(alarm: AlarmEntity) {
            val hour12 = if (alarm.hour % 12 == 0) 12 else alarm.hour % 12
            val amPm   = if (alarm.hour < 12) "AM" else "PM"
            b.tvAmPm.text = amPm
            b.tvTime.text = "%02d:%02d".format(hour12, alarm.minute)
            b.tvLabel.text = if (alarm.label.isEmpty()) b.root.context.getString(R.string.label_default_alarm_label) else alarm.label
            b.tvDays.text  = buildDaysSpan(b.tvDays.context, alarm.repeatDays)

            // 날씨연동 ON 뱃지 + 민감도 요약
            if (alarm.weatherTrigger) {
                b.tvWeatherBadge.visibility = View.VISIBLE
                b.tvSensitivitySummary.visibility = View.VISIBLE
                b.tvSensitivitySummary.text = buildSensitivitySummary(b.root.context, alarm)
            } else {
                b.tvWeatherBadge.visibility = View.GONE
                b.tvSensitivitySummary.visibility = View.GONE
            }

            // 앞당겨진 경우 이유 표시
            if (alarm.isMoved && alarm.movedReason.isNotEmpty()) {
                b.tvMoved.text = movedReasonCardText(b.root.context, alarm.movedReason)
                b.tvMoved.visibility = View.VISIBLE
            } else {
                b.tvMoved.visibility = View.GONE
            }

            // ── 선택 모드 UI ──────────────────────────────────
            val isSelected = alarm.id in selectedIds
            if (isSelectionMode) {
                // 체크박스 표시, 삭제 버튼/스위치 숨김
                b.checkSelect.visibility  = View.VISIBLE
                b.checkSelect.isChecked   = isSelected
                b.btnDelete.visibility    = View.GONE
                b.switchEnabled.visibility = View.GONE
                b.switchWeather.visibility = View.GONE
                // 카드 배경으로 선택 표시
                b.root.isChecked = isSelected
            } else {
                b.checkSelect.visibility  = View.GONE
                b.btnDelete.visibility    = View.VISIBLE
                b.switchEnabled.visibility = View.VISIBLE
                b.switchWeather.visibility = View.VISIBLE
                b.root.isChecked = false
            }

            // ── 스위치 ────────────────────────────────────────
            // 반드시 listener null → isChecked → listener 재등록 순서로 처리해야
            // RecyclerView ViewHolder 재활용 시 이전 리스너가 잘못 트리거되지 않음
            b.switchEnabled.setOnCheckedChangeListener(null)
            b.switchEnabled.isChecked = alarm.isEnabled
            updateAlarmStatus(b, alarm.isEnabled)
            b.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                updateAlarmStatus(b, isChecked)
                onToggle(alarm)
            }

            // 알람 OFF 시 카드 전체 시각적으로 흐리게 (스위치 자체는 또렷하게 유지)
            applyEnabledAppearance(b, alarm.isEnabled)

            b.switchWeather.setOnCheckedChangeListener(null)
            b.switchWeather.isChecked = alarm.weatherTrigger
            b.switchWeather.setOnCheckedChangeListener { _, isChecked ->
                updateWeatherStatus(b, isChecked)
                onWeatherToggle(alarm)
            }

            // 날씨 연동 ON/OFF 상태 표시
            updateWeatherStatus(b, alarm.weatherTrigger)

            // ── 클릭 ──────────────────────────────────────────
            b.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(alarm.id)
                } else {
                    onEdit(alarm)
                }
            }

            // ── 길게 누르기: 선택 모드 진입 ──────────────────
            b.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                }
                toggleSelection(alarm.id)
                true
            }

            b.btnDelete.setOnClickListener { onDelete(alarm) }
        }
    }

    /**
     * 알람 ON/OFF에 따라 카드의 시각적 강조를 조절.
     * 스위치(switchEnabled)와 삭제 버튼은 또렷하게 유지 — 사용자가 언제든 다시 켜거나 삭제할 수 있어야 함.
     * 나머지 콘텐츠(시각/라벨/요일/날씨 영역)는 흐리게 처리해 다른 활성 알람과 시각적으로 확실히 구분.
     */
    private fun applyEnabledAppearance(b: ItemAlarmBinding, isEnabled: Boolean) {
        val contentAlpha = if (isEnabled) 1f else 0.4f
        b.tvAmPm.alpha = contentAlpha
        b.tvTime.alpha = contentAlpha
        b.tvLabel.alpha = contentAlpha
        b.tvDays.alpha = contentAlpha
        b.tvWeatherBadge.alpha = contentAlpha
        b.tvSensitivitySummary.alpha = contentAlpha
        b.tvWeatherLabel.alpha = contentAlpha
        b.tvWeatherStatus.alpha = contentAlpha
        b.switchWeather.alpha = contentAlpha
        b.tvMoved.alpha = contentAlpha
        // switchEnabled, btnDelete는 알파 변경하지 않음 (항상 또렷하게)
    }

    private fun updateAlarmStatus(b: ItemAlarmBinding, isOn: Boolean) {
        if (isOn) {
            b.tvAlarmLabel.alpha   = 1f
            b.tvAlarmStatus.text   = "ON"
            b.tvAlarmStatus.setTextColor(
                ContextCompat.getColor(b.root.context, R.color.cyan_weather)
            )
            b.tvAlarmStatus.setBackgroundResource(R.drawable.bg_weather_badge)
        } else {
            b.tvAlarmLabel.alpha   = 0.4f
            b.tvAlarmStatus.text   = "OFF"
            b.tvAlarmStatus.setTextColor(
                ContextCompat.getColor(b.root.context, R.color.text_hint)
            )
            b.tvAlarmStatus.setBackgroundResource(R.drawable.bg_status_off)
        }
    }

    private fun updateWeatherStatus(b: ItemAlarmBinding, isOn: Boolean) {
        if (isOn) {
            b.tvWeatherLabel.alpha  = 1f
            b.tvWeatherStatus.text  = "ON"
            b.tvWeatherStatus.setTextColor(
                ContextCompat.getColor(b.root.context, R.color.cyan_weather)
            )
            b.tvWeatherStatus.setBackgroundResource(R.drawable.bg_weather_badge)
        } else {
            b.tvWeatherLabel.alpha  = 0.4f
            b.tvWeatherStatus.text  = "OFF"
            b.tvWeatherStatus.setTextColor(
                ContextCompat.getColor(b.root.context, R.color.text_hint)
            )
            b.tvWeatherStatus.setBackgroundResource(R.drawable.bg_status_off)
        }
    }

    private fun toggleSelection(alarmId: Int) {
        if (alarmId in selectedIds) selectedIds.remove(alarmId)
        else selectedIds.add(alarmId)

        // 선택이 하나도 없으면 선택 모드 해제
        if (selectedIds.isEmpty()) {
            isSelectionMode = false
        }

        notifyDataSetChanged()
        onSelectionChanged(isSelectionMode, selectedIds.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AlarmEntity>() {
            override fun areItemsTheSame(a: AlarmEntity, b: AlarmEntity) = a.id == b.id
            override fun areContentsTheSame(a: AlarmEntity, b: AlarmEntity) = a == b
        }

        private fun dayLabels(context: android.content.Context) = listOf(
            context.getString(R.string.day_sun),
            context.getString(R.string.day_mon),
            context.getString(R.string.day_tue),
            context.getString(R.string.day_wed),
            context.getString(R.string.day_thu),
            context.getString(R.string.day_fri),
            context.getString(R.string.day_sat)
        )

        /**
         * 알람 카드용 문구 생성.
         * 신규 데이터(RAIN/SNOW 코드)는 공급자별 완성형 문장을 그대로 반환.
         * 레거시 데이터(원문 저장)는 _format 폴백으로 감싸 조사 어색함을 최소화.
         */
        private fun movedReasonCardText(context: android.content.Context, code: String): String = when (code) {
            "RAIN" -> context.getString(R.string.moved_reason_card_rain)
            "SNOW" -> context.getString(R.string.moved_reason_card_snow)
            else -> context.getString(R.string.moved_reason_format, code)
        }

        /**
         * 7개 요일을 항상 표시하되, 활성 요일은 파란색+굵게, 비활성 요일은 흐리게 처리.
         * mask == 0 (반복 없음)이면 "1회성 알람" 텍스트를 흐리게 표시.
         * 예) 주중 알람 → "일 월 화 수 목 금 토" (일·토는 흐리게)
         */
        fun buildDaysSpan(context: android.content.Context, mask: Int): SpannableStringBuilder {
            val activeColor   = ContextCompat.getColor(context, R.color.primary_blue)
            val inactiveColor = ContextCompat.getColor(context, R.color.text_secondary)

            // 반복 없음 (1회성 알람)
            if (mask == 0) {
                val text = context.getString(R.string.label_one_time_alarm)
                val sb = SpannableStringBuilder(text)
                sb.setSpan(ForegroundColorSpan(inactiveColor), 0, sb.length, 0)
                return sb
            }

            val labels = dayLabels(context)
            val sb = SpannableStringBuilder()
            labels.forEachIndexed { i, label ->
                if (i > 0) sb.append(" ")
                val start = sb.length
                sb.append(label)
                val end = sb.length
                val isActive = (mask and (1 shl i)) != 0
                if (isActive) {
                    sb.setSpan(ForegroundColorSpan(activeColor), start, end, 0)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
                } else {
                    sb.setSpan(ForegroundColorSpan(inactiveColor), start, end, 0)
                }
            }
            return sb
        }

        /** 민감도 레벨 → 짧은 레이블 */
        private fun sensitivityLabel(context: android.content.Context, level: Int) = when (level) {
            0 -> context.getString(R.string.sensitivity_very)
            1 -> context.getString(R.string.sensitivity_sensitive)
            2 -> context.getString(R.string.sensitivity_normal)
            3 -> context.getString(R.string.sensitivity_low)
            else -> context.getString(R.string.sensitivity_normal)
        }

        /** 앞당김 분 → 짧은 표시 */
        private fun advanceLabel(context: android.content.Context, min: Int) = when (min) {
            0  -> context.getString(R.string.advance_none)
            60 -> context.getString(R.string.advance_one_hour)
            else -> context.getString(R.string.advance_minutes_format, min)
        }

        /** 알람 카드 민감도 요약 줄 텍스트 (한 줄에 들어가도록 컴팩트하게) */
        fun buildSensitivitySummary(context: android.content.Context, alarm: AlarmEntity): String {
            val rain = "☔${sensitivityLabel(context, alarm.rainSensitivity)}·${advanceLabel(context, alarm.rainAdvanceMin)}↑"
            val snow = "❄️${sensitivityLabel(context, alarm.snowSensitivity)}·${advanceLabel(context, alarm.snowAdvanceMin)}↑"
            return "$rain  $snow"
        }

        fun formatRepeatDays(context: android.content.Context, mask: Int): String = when {
            mask == 0 -> context.getString(R.string.repeat_no_repeat)
            mask == 0b1111111 -> context.getString(R.string.repeat_every_day)
            mask == 0b0111110 -> context.getString(R.string.repeat_weekdays)  // 월~금 = bit 1~5
            mask == 0b1000001 -> context.getString(R.string.repeat_weekends)  // 일+토 = bit 0, 6
            else -> {
                val labels = dayLabels(context)
                (0..6).filter { (mask and (1 shl it)) != 0 }.joinToString(" ") { labels[it] }
            }
        }
    }
}
