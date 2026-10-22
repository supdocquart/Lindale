/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels
import com.metrolist.music.platform.AppContext
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.HistoryPage
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HistorySource
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel
constructor(
    private val context: AppContext,
    val database: MusicDatabase,
) : ViewModel() {
    var historySource = MutableStateFlow(HistorySource.LOCAL)

    private val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timeZone).date
    private val thisMonday = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
    private val lastMonday = thisMonday.minus(7, DateTimeUnit.DAY)

    val historyPage = MutableStateFlow<HistoryPage?>(null)

    val events =
        context.dataStore.data
            .map { it[HideVideoSongsKey] ?: false }
            .distinctUntilChanged()
            .flatMapLatest { hideVideoSongs ->
                database
                    .historyEvents(
                        tomorrowStart = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toLocalDateTime(timeZone),
                        todayStart = today.atStartOfDayIn(timeZone).toLocalDateTime(timeZone),
                        yesterdayStart = today.minus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toLocalDateTime(timeZone),
                        thisMondayStart = thisMonday.atStartOfDayIn(timeZone).toLocalDateTime(timeZone),
                        lastMondayStart = lastMonday.atStartOfDayIn(timeZone).toLocalDateTime(timeZone),
                    )
                    .map { events ->
                        events
                            .filter { !hideVideoSongs || !it.song.song.isVideo }
                            .groupBy {
                                val date = it.event.timestamp.date
                                val daysAgo = date.daysUntil(today)
                                when {
                                    daysAgo == 0 -> DateAgo.Today
                                    daysAgo == 1 -> DateAgo.Yesterday
                                    date >= thisMonday -> DateAgo.ThisWeek
                                    date >= lastMonday -> DateAgo.LastWeek
                                    else -> DateAgo.Other(LocalDate(date.year, date.month, 1))
                                }
                            }.toSortedMap(
                                compareBy { dateAgo ->
                                    when (dateAgo) {
                                        DateAgo.Today -> 0L
                                        DateAgo.Yesterday -> 1L
                                        DateAgo.ThisWeek -> 2L
                                        DateAgo.LastWeek -> 3L
                                        is DateAgo.Other -> dateAgo.date.daysUntil(today).toLong()
                                    }
                                },
                            ).mapValues { entry ->
                                entry.value.distinctBy { it.song.id }
                            }
                    }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    init {
        fetchRemoteHistory()
    }

    fun fetchRemoteHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.musicHistory().onSuccess {
                historyPage.value = it
            }.onFailure {
                reportException(it)
            }
        }
    }
}

sealed class DateAgo {
    data object Today : DateAgo()

    data object Yesterday : DateAgo()

    data object ThisWeek : DateAgo()

    data object LastWeek : DateAgo()

    class Other(
        val date: LocalDate,
    ) : DateAgo() {
        override fun equals(other: Any?): Boolean {
            if (other is Other) return date == other.date
            return super.equals(other)
        }

        override fun hashCode(): Int = date.hashCode()
    }
}







