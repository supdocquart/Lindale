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
@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val playlistId: String? = null,
    val title: String,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val themeColor: Int? = null,
    val songCount: Int,
    val duration: Int,
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val lastUpdateTime: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    val bookmarkedAt: LocalDateTime? = null,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    @ColumnInfo(name = "isUploaded", defaultValue = false.toString())
    val isUploaded: Boolean = false
) {
    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    )

    fun toggleUploaded() = copy(
        isUploaded = !isUploaded
    )

    fun toggleLibrary() = copy(
        inLibrary = if (inLibrary != null) null else Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    )

    fun toggleLike() = localToggleLike().also {
        CoroutineScope(Dispatchers.IO).launch {
            if (playlistId != null)
                YouTube.likePlaylist(playlistId, bookmarkedAt == null)
        }
    }
}


