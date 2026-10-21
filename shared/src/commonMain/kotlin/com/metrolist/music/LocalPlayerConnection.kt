package com.metrolist.music
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import androidx.compose.runtime.staticCompositionLocalOf
import com.metrolist.music.playback.PlayerConnection

val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { null }
