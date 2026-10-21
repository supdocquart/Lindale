/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import com.metrolist.music.playback.MediaItem
import com.metrolist.music.models.MediaMetadata

class ListQueue(
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus() = Queue.Status(title, items, startIndex, position)

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage() = throw UnsupportedOperationException()
}
