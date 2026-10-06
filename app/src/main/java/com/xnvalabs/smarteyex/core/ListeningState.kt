package com.xnvalabs.smarteyex.core

import androidx.compose.runtime.mutableStateOf

/** Observable state of the always-on mic service (set only by ListeningService). */
object ListeningState {
    val active = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
}

/** True while a SmartEyeX activity is visible. Android only lets a visible app start other apps directly. */
object AppVisibility {
    @Volatile
    var foreground: Boolean = false
}
