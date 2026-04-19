package com.devkorea1m.weatherwake.sound

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.devkorea1m.weatherwake.databinding.ItemSoundBinding

class SoundListAdapter(
    private val context: Context,
    private val sounds: List<AlarmSound>,
    selectedUri: String,
    private val onSelect: (AlarmSound) -> Unit
) : RecyclerView.Adapter<SoundListAdapter.VH>() {

    // 선택된 position 추적 (전체 갱신 없이 해당 셀만 업데이트)
    private var selectedPos: Int = sounds.indexOfFirst { it.id == selectedUri }

    // 현재 재생 중인 position 추적
    private var playingPos: Int = -1

    inner class VH(val b: ItemSoundBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = sounds.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val sound = sounds[position]
        holder.b.tvSoundName.text   = sound.name
        holder.b.radioBtn.isChecked = position == selectedPos
        holder.b.btnPreview.setImageResource(
            if (position == playingPos) android.R.drawable.ic_media_pause
            else                        android.R.drawable.ic_media_play
        )

        // 행 클릭 → 선택
        holder.itemView.setOnClickListener {
            val prev = selectedPos
            selectedPos = position
            if (prev != -1) notifyItemChanged(prev)   // 이전 선택 해제
            notifyItemChanged(position)                // 새 선택 표시

            onSelect(sound)
            stopPreviewInternal()
        }

        // 미리듣기 버튼 → 재생/정지 토글
        holder.b.btnPreview.setOnClickListener {
            val playing = PreviewPlayer.toggle(
                context = context,
                sound   = sound,
                onStop  = { onPreviewStop() }
            )
            val prev = playingPos
            playingPos = if (playing) position else -1
            if (prev != -1) notifyItemChanged(prev)   // 이전 재생 아이콘 갱신
            notifyItemChanged(position)                // 현재 아이콘 갱신
        }
    }

    /** PreviewPlayer 재생 완료 콜백 */
    private fun onPreviewStop() {
        val prev = playingPos
        playingPos = -1
        if (prev != -1) notifyItemChanged(prev)
    }

    /** 탭 전환 등 외부에서 중단 요청 */
    fun stopIfPlaying() {
        if (sounds.any { it.id == PreviewPlayer.currentId }) {
            PreviewPlayer.stop()
            val prev = playingPos
            playingPos = -1
            if (prev != -1) notifyItemChanged(prev)
        }
    }

    private fun stopPreviewInternal() {
        val prev = playingPos
        playingPos = -1
        PreviewPlayer.stop()
        if (prev != -1) notifyItemChanged(prev)
    }
}
