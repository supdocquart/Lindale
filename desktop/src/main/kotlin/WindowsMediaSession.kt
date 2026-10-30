import com.metrolist.music.playback.PlayerConnection
import dev.nucleusframework.media.control.MediaControlEvent
import dev.nucleusframework.media.control.MediaControlService
import dev.nucleusframework.media.control.MediaMetadata
import dev.nucleusframework.media.control.MediaPlaybackState
import dev.nucleusframework.media.control.MediaPlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Registers Lindale with Windows' media session (SMTC): the volume/lock-screen media overlay
 * shows the song, and keyboard media keys (play/pause, next, previous) control the player.
 */
class WindowsMediaSession(private val connection: PlayerConnection) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        val ok = runCatching {
            if (!MediaControlService.isAvailable()) {
                Timber.w("media session: native bridge not loaded, Windows media controls disabled")
                return@runCatching false
            }
            // No arguments: on Windows the identity is the app's AppUserModelID, which the library
            // derives itself; a made-up id is not tied to any Start menu entry and the overlay ignores it.
            MediaControlService.configure()
            MediaControlService.attach(::onEvent)
            Timber.i("media session: Windows media controls ready")
            true
        }.onFailure { Timber.w(it, "media session: unavailable") }.getOrDefault(false)
        if (!ok) return

        scope.launch {
            combine(connection.mediaMetadata, connection.isPlaying) { m, p -> m to p }.collect { (m, playing) ->
                runCatching {
                    if (m == null) {
                        MediaControlService.setPlaybackState(MediaPlaybackState(MediaPlaybackStatus.STOPPED, null))
                        return@runCatching
                    }
                    MediaControlService.setMetadata(
                        MediaMetadata(
                            m.title,
                            m.artists.joinToString { it.name },
                            m.album?.title,
                            m.thumbnailUrl,
                            m.duration.takeIf { it > 0 }?.times(1000L),
                        ),
                    )
                    pushState(playing)
                }.onFailure { Timber.w(it, "media session: update failed") }
            }
        }
        // Keeps the overlay's progress bar honest after seeks.
        scope.launch {
            while (true) {
                delay(5_000)
                if (connection.mediaMetadata.value != null) runCatching { pushState(connection.isPlaying.value) }
            }
        }
    }

    private fun pushState(playing: Boolean) {
        MediaControlService.setPlaybackState(
            MediaPlaybackState(
                if (playing) MediaPlaybackStatus.PLAYING else MediaPlaybackStatus.PAUSED,
                connection.player.currentPosition.coerceAtLeast(0L),
            ),
        )
    }

    // Events arrive on a native thread; the player is driven from the UI thread like the shortcuts are.
    private fun onEvent(e: MediaControlEvent) = javax.swing.SwingUtilities.invokeLater { handle(e) }

    private fun handle(e: MediaControlEvent) {
        Timber.d("media session: %s", e::class.simpleName)
        when (e) {
            is MediaControlEvent.Play -> connection.play()
            is MediaControlEvent.Pause, is MediaControlEvent.Stop -> connection.pause()
            is MediaControlEvent.Toggle -> connection.togglePlayPause()
            is MediaControlEvent.Next -> connection.seekToNext()
            is MediaControlEvent.Previous -> connection.seekToPrevious()
            is MediaControlEvent.SetPosition -> connection.seekTo(e.positionMs)
            is MediaControlEvent.SeekBy -> connection.seekTo((connection.player.currentPosition + e.offsetMs).coerceAtLeast(0L))
            else -> Unit
        }
    }
}
