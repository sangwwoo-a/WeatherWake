package com.devkorea1m.weatherwake.sound

import android.net.Uri

/**
 * 알람음 항목 하나를 나타내는 모델
 *
 * @param id       목록 내 고유 id (URI 문자열 사용)
 * @param name     화면에 표시될 이름
 * @param uri      재생 URI (content:// or android.resource://)
 * @param category 소리 크기 분류
 */
data class AlarmSound(
    val id: String,
    val name: String,
    val uri: Uri,
    val category: SoundCategory
)
