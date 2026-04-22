package com.devkorea1m.weatherwake.sound

import androidx.annotation.StringRes
import com.devkorea1m.weatherwake.R

enum class SoundCategory(@StringRes val labelRes: Int) {
    LOUD(R.string.sound_category_loud),
    NORMAL(R.string.sound_category_normal),
    CALM(R.string.sound_category_calm)
}
