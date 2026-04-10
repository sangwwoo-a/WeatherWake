package com.devkorea1m.weatherwake.sound

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SoundPickerViewModel : ViewModel() {
    val selectedUri = MutableLiveData<String>()
    var selectedName: String = ""

    fun select(sound: AlarmSound) {
        selectedUri.value = sound.id
        selectedName = sound.name
    }

    fun init(uri: String, name: String) {
        if (selectedUri.value == null) {
            selectedUri.value = uri
            selectedName = name
        }
    }
}
