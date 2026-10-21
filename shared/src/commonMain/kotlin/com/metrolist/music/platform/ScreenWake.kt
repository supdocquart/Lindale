package com.metrolist.music.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/** Keeps the display from sleeping while at least one holder is active. */
object ScreenWake {
    @Volatile
    var backend: ((keepAwake: Boolean) -> Unit)? = null

    private var holders = 0

    @Synchronized
    fun acquire() {
        if (holders++ == 0) backend?.invoke(true)
    }

    @Synchronized
    fun release() {
        if (holders > 0 && --holders == 0) backend?.invoke(false)
    }
}

@Composable
fun KeepScreenAwake(enabled: Boolean) {
    DisposableEffect(enabled) {
        if (enabled) ScreenWake.acquire()
        onDispose { if (enabled) ScreenWake.release() }
    }
}
