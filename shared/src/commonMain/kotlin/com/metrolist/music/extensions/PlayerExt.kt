package com.metrolist.music.extensions

import androidx.media3.common.Player

fun Player.toggleRepeatMode() {
    val next = when (repeatMode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }
    setRepeatMode(next)
}

fun Player.togglePlayPause() {
    if (isPlaying) pause() else play()
}
