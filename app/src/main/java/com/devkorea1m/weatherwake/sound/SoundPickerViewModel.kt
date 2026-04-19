package com.devkorea1m.weatherwake.sound

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/**
 * SavedStateHandle을 사용해 프로세스 종료 후 재시작 시에도
 * 선택된 알람음 정보를 복원한다.
 */
class SoundPickerViewModel(private val state: SavedStateHandle) : ViewModel() {

    /** 선택된 알람음 URI — 화면 회전, 프로세스 킬 후에도 유지 */
    val selectedUri = state.getLiveData(KEY_URI, "")

    /** 선택된 알람음 이름 */
    var selectedName: String
        get() = state.get<String>(KEY_NAME) ?: ""
        set(value) { state[KEY_NAME] = value }

    fun select(sound: AlarmSound) {
        selectedUri.value = sound.id
        selectedName      = sound.name
    }

    /**
     * 초기값 설정 — 이미 값이 있으면(화면 회전 복귀 등) 덮어쓰지 않는다.
     */
    fun init(uri: String, name: String) {
        if (selectedUri.value.isNullOrEmpty()) {
            selectedUri.value = uri
            selectedName      = name
        }
    }

    companion object {
        private const val KEY_URI  = "selected_uri"
        private const val KEY_NAME = "selected_name"
    }
}
