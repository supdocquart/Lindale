package com.metrolist.music.ui.screens.wrapped

import androidx.compose.runtime.compositionLocalOf

internal val LocalWrappedManager = compositionLocalOf<WrappedManager> { error("No WrappedManager found!") }
