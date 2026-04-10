package com.devkorea1m.weatherwake

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.devkorea1m.weatherwake.data.model.AlarmEntity
import com.devkorea1m.weatherwake.databinding.ItemAlarmBinding

class AlarmAdapter(
    private val onToggle: (AlarmEntity) -> Unit,
    private val onWeatherToggle: (AlarmEntity) -> Unit,
    private val onEdit: (AlarmEntity) -> Unit,
    private val onDelete: (AlarmEntity) -> Unit
) : ListAdapter<AlarmEntity, AlarmAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val b: ItemAlarmBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(alarm: AlarmEntity) {
            val hour12 = if (alarm.hour % 12 == 0) 12 else alarm.hour % 12
            val amPm   = if (alarm.hour < 12) "AM" else "PM"
            b.tvTime.text  = "%s %02d:%02d".format(amPm, hour12, alarm.minute)
            b.tvLabel.text = alarm.label

            // 앞당겨진 경우 이유 표시
            if (alarm.isMoved && alarm.movedReason.isNotEmpty()) {
                b.tvMoved.text = "⏰ ${alarm.movedReason}으로 앞당겨졌어요"
                b.tvMoved.visibility = android.view.View.VISIBLE
            } else {
                b.tvMoved.visibility = android.view.View.GONE
            }

            // 알람 ON/OFF
            b.switchEnabled.isChecked = alarm.isEnabled
            b.switchEnabled.setOnCheckedChangeListener(null)
            b.switchEnabled.setOnCheckedChangeListener { _, _ -> onToggle(alarm) }

            // 날씨 연동 ON/OFF
            b.switchWeather.isChecked = alarm.weatherTrigger
            b.switchWeather.setOnCheckedChangeListener(null)
            b.switchWeather.setOnCheckedChangeListener { _, _ -> onWeatherToggle(alarm) }

            b.root.setOnClickListener { onEdit(alarm) }
            b.btnDelete.setOnClickListener { onDelete(alarm) }
        }
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
    }
}
