package com.metrolist.music.ui.screens.wrapped
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import com.metrolist.music.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class WrappedAudioService(
    private val context: PlatformContext,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    suspend fun prepareTrack(songId: String?) {
        Timber.tag("WrappedAudioService").d("prepareTrack: ")
    }

    fun playTrack(songId: String?) {
        Timber.tag("WrappedAudioService").d("playTrack: ")
    }

    fun pause() {}

    fun resume() {}

    fun release() {
        Timber.tag("WrappedAudioService").d("WrappedAudioService released")
    }
}
