package com.metrolist.music.utils
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Simple singleton to trigger podcast library refresh from anywhere.
 * Used when subscribing/unsubscribing from channels.
 */
object PodcastRefreshTrigger {
    private val _refreshFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshFlow = _refreshFlow.asSharedFlow()

    fun triggerRefresh() {
        _refreshFlow.tryEmit(Unit)
    }
}
