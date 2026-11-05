package com.metrolist.music.platform

import kotlinx.coroutines.flow.MutableStateFlow

/** DESKTOP: whether the small always-on-top player window is shown instead of the main window. */
object MiniPlayerState {
    val visible = MutableStateFlow(false)
}
