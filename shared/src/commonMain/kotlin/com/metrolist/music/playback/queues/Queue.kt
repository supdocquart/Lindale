/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import com.metrolist.innertube.models.dropCleanDuplicates
import com.metrolist.innertube.models.normalizeTitleKey
import com.metrolist.music.playback.MediaItem
import com.metrolist.music.extensions.metadata
import com.metrolist.music.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        // DESKTOP: also run when only "hide clean duplicates" is on.
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled || com.metrolist.innertube.models.CleanDuplicates.enabled) {
                copy(
                    items = items.filterExplicit(enabled),
                )
            } else {
                this
            }

        fun filterVideoSongs(disableVideos: Boolean = false) =
            if (disableVideos) {
                copy(
                    items = items.filterVideoSongs(true),
                )
            } else {
                this
            }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    // DESKTOP: hide clean twins when an explicit release of the same song is in the list.
    (if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }).dropCleanDuplicates(
        isExplicit = { it.metadata?.explicit == true },
        key = { item ->
            item.metadata?.let { meta ->
                val base = normalizeTitleKey(meta.title, meta.artists.map { it.name })
                if (meta.duration > 0) "$base|${meta.duration / 3}" else base
            }
        },
    )

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }
