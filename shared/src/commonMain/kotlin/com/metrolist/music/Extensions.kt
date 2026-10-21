package com.metrolist.music
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import com.metrolist.music.playback.MediaItem

fun Any?.toMediaItem(): MediaItem = MediaItem()
