/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db.entities
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

@Immutable
@Entity(tableName = "artist")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val lastUpdateTime: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    val bookmarkedAt: LocalDateTime? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    @ColumnInfo(name = "isPodcastChannel", defaultValue = false.toString())
    val isPodcastChannel: Boolean = false,
    // The artist page as YouTube returned it, so the screen has something to draw before the
    // network answers. Read only by ArtistViewModel, for the single artist on screen. Relations
    // that pull artists in bulk project the other columns explicitly, because Room builds one
    // ArtistEntity per pairing and would otherwise hold one copy of this page per song.
    @ColumnInfo(name = "cachedPageJson")
    val cachedPageJson: String? = null
) {
    val isYouTubeArtist: Boolean
        get() = id.startsWith("UC") || id.startsWith("FEmusic_library_privately_owned_artist")

    val isPrivatelyOwnedArtist: Boolean
        get() = id.startsWith("FEmusic_library_privately_owned_artist")

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    )

    fun toggleLike() = localToggleLike().also {
        CoroutineScope(Dispatchers.IO).launch {
            val targetChannelId = channelId ?: YouTube.getChannelId(id)
            if (targetChannelId.isNotEmpty()) {
                YouTube.subscribeChannel(targetChannelId, bookmarkedAt == null)
            }
        }
    }

    companion object {
        fun generateArtistId() = generateLocalId("LA")
    }
}


