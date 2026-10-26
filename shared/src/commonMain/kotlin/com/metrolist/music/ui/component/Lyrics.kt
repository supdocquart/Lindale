/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component
import androidx.compose.runtime.getValue
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource


import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.metrolist.music.viewmodels.metrolistViewModel
import com.metrolist.music.constants.ExperimentalLyricsKey
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LyricsViewModel

@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    showLyrics: Boolean,
    lyricsViewModel: LyricsViewModel = metrolistViewModel()
) {
    val (experimentalLyrics, _) = rememberPreference(key = ExperimentalLyricsKey, defaultValue = true)

    // DESKTOP: overlay the sync banner while "Sync lyrics" mode is on.
    val syncActive by LyricsSync.active.collectAsState()
    Box(modifier) {
    if (experimentalLyrics) {
        ExperimentalLyrics(
            sliderPositionProvider = sliderPositionProvider,
            modifier = Modifier.fillMaxSize(),
            showLyrics = showLyrics,
            lyricsViewModel = lyricsViewModel
        )
    } else {
        OriginalLyrics(
            sliderPositionProvider = sliderPositionProvider,
            modifier = Modifier.fillMaxSize(),
            showLyrics = showLyrics
        )
    }
    if (syncActive && showLyrics) {
        LyricsSyncBanner(Modifier.align(androidx.compose.ui.Alignment.TopCenter))
    }
    }
}
