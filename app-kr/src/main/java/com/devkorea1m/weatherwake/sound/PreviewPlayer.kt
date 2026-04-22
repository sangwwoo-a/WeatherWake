package com.devkorea1m.weatherwake.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * 앱 전역에서 단 하나의 미리듣기 MediaPlayer를 관리하는 싱글턴.
 * 탭이 달라도 이전 재생을 자동으로 멈춘다.
 */
object PreviewPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var playingId: String = ""

    /** 현재 재생 중인 sound id */
    val currentId: String get() = playingId

    /**
     * 재생 요청. 이미 같은 id가 재생 중이면 멈춤(토글).
     * @return 재생을 시작하면 true, 멈추면 false
     */
    fun toggle(context: Context, sound: AlarmSound, onStop: () -> Unit): Boolean {
        return if (playingId == sound.id && mediaPlayer?.isPlaying == true) {
            stop()
            onStop()
            false
        } else {
            play(context, sound, onStop)
            true
        }
    }

    fun play(context: Context, sound: AlarmSound, onStop: () -> Unit) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, sound.uri)
                prepare()
                isLooping = false
                setOnCompletionListener {
                    playingId = ""
                    release()
                    mediaPlayer = null
                    onStop()
                }
                start()
            }
            playingId = sound.id
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
            onStop()
        }
    }

    fun stop() {
        runCatching { mediaPlayer?.stop(); mediaPlayer?.release() }
        mediaPlayer = null
        playingId = ""
    }
}
