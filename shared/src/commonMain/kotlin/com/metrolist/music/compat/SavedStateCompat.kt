package com.metrolist.music.compat

import androidx.savedstate.SavedState
import androidx.savedstate.read

fun SavedState.getString(key: String): String? =
    read { getStringOrNull(key) }

fun SavedState.getBoolean(key: String): Boolean? =
    read { getBooleanOrNull(key) }
