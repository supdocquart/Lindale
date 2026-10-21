/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.constants
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource


object MediaSessionConstants {
    const val ACTION_TOGGLE_LIBRARY = "TOGGLE_LIBRARY"
    const val ACTION_TOGGLE_START_RADIO = "TOGGLE_START_RADIO"
    const val ACTION_TOGGLE_LIKE = "TOGGLE_LIKE"
    const val ACTION_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
    const val ACTION_TOGGLE_REPEAT_MODE = "TOGGLE_REPEAT_MODE"
    const val ACTION_ADD_TO_TARGET_PLAYLIST = "ADD_TO_TARGET_PLAYLIST"

    val CommandToggleLibrary = Any()
    val CommandToggleLike = Any()
    val CommandToggleStartRadio = Any()
    val CommandToggleShuffle = Any()
    val CommandToggleRepeatMode = Any()
    val CommandAddToTargetPlaylist = Any()

    const val TARGET_PLAYLIST_AUTO = "auto"
}

