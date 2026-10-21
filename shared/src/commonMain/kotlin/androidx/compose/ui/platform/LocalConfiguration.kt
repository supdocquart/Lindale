package androidx.compose.ui.platform

import androidx.compose.runtime.compositionLocalOf
import android.content.res.Configuration

typealias Configuration = android.content.res.Configuration

val LocalConfiguration = compositionLocalOf { Configuration() }
