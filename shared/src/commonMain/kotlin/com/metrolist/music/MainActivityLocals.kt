package com.metrolist.music
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.MutableState
import androidx.navigation.NavController
import com.metrolist.music.db.MusicDatabase

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalSyncUtils = staticCompositionLocalOf<com.metrolist.music.utils.SyncUtils> { error("No SyncUtils provided") }
val LocalListenTogetherManager = staticCompositionLocalOf<com.metrolist.music.listentogether.ListenTogetherManager?> { null }
val LocalChangelogState = staticCompositionLocalOf<MutableState<Boolean>> { error("No LocalChangelogState provided") }
val LocalArtistNameAliases = staticCompositionLocalOf<Map<String, String>> { emptyMap() }
val LocalIsPlayerExpanded = compositionLocalOf { false }
val LocalDownloadUtil = staticCompositionLocalOf<com.metrolist.music.ui.component.DownloadUtil> { error("No DownloadUtil provided") }
