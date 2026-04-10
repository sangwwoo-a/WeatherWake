package com.devkorea1m.weatherwake.sound

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.devkorea1m.weatherwake.databinding.ItemSoundBinding

class SoundListAdapter(
    private val context: Context,
    private val sounds: List<AlarmSound>,
    private var selectedUri: String,
    private val onSelect: (AlarmSound) -> Unit
) : RecyclerView.Adapter<SoundListAdapter.VH>() {

    inner class VH(val b: ItemSoundBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = sounds.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val sound = sounds[position]
        holder.b.tvSoundName.text   = sound.name
        holder.b.radioBtn.isChecked = sound.id == selectedUri

        val isPlaying = PreviewPlayer.currentId == sound.id
        holder.b.btnPreview.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else           android.R.drawable.ic_media_play
        )

        // 행 클릭 → 선택
        holder.itemView.setOnClickListener {
            selectedUri = sound.id
            notifyDataSetChanged()
            onSelect(sound)
            PreviewPlayer.stop()
        }

        // 미리듣기 버튼 → 전역 플레이어로 재생/정지 토글
        holder.b.btnPreview.setOnClickListener {
            PreviewPlayer.toggle(
                context = context,
                sound   = sound,
                onStop  = { notifyDataSetChanged() }   // 재생 완료 시 아이콘 갱신
            )
            notifyDataSetChanged()  // 재생 시작/정지 즉시 아이콘 갱신
        }
    }

    /** 탭 전환 등 외부에서 중단이 필요할 때 호출 — 이 어댑터가 재생 중이면 멈춤 */
    fun stopIfPlaying() {
        if (sounds.any { it.id == PreviewPlayer.currentId }) {
            PreviewPlayer.stop()
            notifyDataSetChanged()
        }
    }
}
