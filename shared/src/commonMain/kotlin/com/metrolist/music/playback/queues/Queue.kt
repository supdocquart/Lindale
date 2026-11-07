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
                filterItems { it.filterExplicit(enabled) }
            } else {
                this
            }

        fun filterVideoSongs(disableVideos: Boolean = false) =
            if (disableVideos) filterItems { it.filterVideoSongs(true) } else this

        // Keeps mediaItemIndex on the same song; if that song is removed, starts at the next kept one.
        // DESKTOP: takes a list filter instead of a per-item test so "hide clean duplicates", which
        // compares songs with each other, keeps working.
        private fun filterItems(filter: (List<MediaItem>) -> List<MediaItem>): Status {
            val filtered = filter(items)
            if (filtered.size == items.size) return this
            val kept = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<MediaItem, Boolean>())
            kept.addAll(filtered)
            val start = items.getOrNull(mediaItemIndex)
            val keptBefore = items.take(mediaItemIndex.coerceAtLeast(0)).count { it in kept }
            return copy(
                items = filtered,
                mediaItemIndex = keptBefore.coerceAtMost((filtered.size - 1).coerceAtLeast(0)),
                position = if (start != null && start in kept) position else 0L,
            )
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
