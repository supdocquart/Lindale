/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import com.metrolist.music.PlatformContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.MyTopFilter
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class TopPlaylistViewModel
constructor(
    context: PlatformContext,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val top = savedStateHandle.get<String>("top")!!

    val topPeriod = MutableStateFlow(MyTopFilter.ALL_TIME)

    @OptIn(ExperimentalCoroutinesApi::class)
    val topSongs =
        combine(
            topPeriod,
            context.dataStore.data.map { it[HideVideoSongsKey] ?: false }.distinctUntilChanged()
        ) { period, hideVideoSongs -> period to hideVideoSongs }
            .flatMapLatest { (period, hideVideoSongs) ->
                val now = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                database.mostPlayedSongs(
                    fromTimeStamp = period.toLocalDateTime(),
                    limit = top.toInt(),
                    offset = 0,
                    toTimeStamp = now
                ).map { songs ->
                    if (hideVideoSongs) songs.filter { !it.song.isVideo } else songs
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
