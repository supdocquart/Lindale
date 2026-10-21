package com.metrolist.music.playback
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

import androidx.media3.common.Timeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.Queue

class Cache {
    val cacheSpace: Long = 0L
    val keys: Set<String> = emptySet()
    fun removeResource(key: String) {}
}

class ConnectivityObserver {
    val networkStatus: StateFlow<Boolean> = MutableStateFlow(true)
}

class SleepTimer(
    private val onExpired: (() -> Unit)? = null,
) {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    private var timerJob: kotlinx.coroutines.Job? = null

    var isActive: Boolean by androidx.compose.runtime.mutableStateOf(false)
    var remainingTime: Long = 0L
    var triggerTime: Long? by androidx.compose.runtime.mutableStateOf(null)
    var pauseWhenSongEnd: Boolean by androidx.compose.runtime.mutableStateOf(false)

    fun start(millis: Long = 0L, pauseWhenSongEnd: Boolean = false) {
        clear()
        this.isActive = true
        this.pauseWhenSongEnd = pauseWhenSongEnd
        if (millis > 0L) {
            val end = System.currentTimeMillis() + millis
            this.triggerTime = end
            this.remainingTime = millis
            timerJob = scope.launchTimer(end)
        }
    }

    fun start(minute: Int = 0, stopAfterCurrentSong: Boolean = false, fadeOut: Boolean = false) {
        clear()
        if (minute == -1) {
            this.isActive = true
            this.pauseWhenSongEnd = true
            this.triggerTime = null
            this.remainingTime = 0L
            return
        }
        if (minute > 0) {
            val millis = minute * 60_000L
            val end = System.currentTimeMillis() + millis
            this.isActive = true
            this.pauseWhenSongEnd = stopAfterCurrentSong
            this.triggerTime = end
            this.remainingTime = millis
            timerJob = scope.launchTimer(end)
        }
    }

    private fun kotlinx.coroutines.CoroutineScope.launchTimer(endTime: Long) =
        launch {
            while (true) {
                val left = endTime - System.currentTimeMillis()
                if (left <= 0L) break
                remainingTime = left
                delay(500L)
            }
            remainingTime = 0L
            triggerTime = null
            if (!pauseWhenSongEnd) {
                isActive = false
                onExpired?.invoke()
            }
        }

    fun clear() {
        timerJob?.cancel()
        timerJob = null
        isActive = false
        remainingTime = 0L
        triggerTime = null
        pauseWhenSongEnd = false
    }

    fun cancel() = clear()
}

class CastConnectionHandler {
    val isCasting: StateFlow<Boolean> = MutableStateFlow(false)
    val castIsPlaying: StateFlow<Boolean> = MutableStateFlow(false)
    val castVolume: StateFlow<Float> = MutableStateFlow(1f)
    val castDeviceName: StateFlow<String?> = MutableStateFlow(null)
    val castPosition: StateFlow<Long> = MutableStateFlow(0L)
    val castDuration: StateFlow<Long> = MutableStateFlow(0L)
    fun play() {}
    fun pause() {}
    fun setVolume(volume: Float) {}
    fun seekTo(position: Long) {}
    fun navigateToMediaIfInQueue(mediaId: String): Boolean = false
}

interface MusicPlayerService {
    val playerCache: Cache?
        get() = Cache()
    val downloadCache: Cache?
        get() = Cache()
    val sleepTimer: SleepTimer
        get() = SleepTimer()
    val castConnectionHandler: CastConnectionHandler?
        get() = null
    val playerVolume: MutableStateFlow<Float>
        get() = MutableStateFlow(1f)
    val automixItems: StateFlow<List<MediaItem>>
        get() = MutableStateFlow(emptyList())
    val isPlayerReady: StateFlow<Boolean>
        get() = MutableStateFlow(true)
    val connectivityObserver: ConnectivityObserver
        get() = ConnectivityObserver()
    fun getAutomix(playlistId: String? = null) {}
    fun toggleLike() {}
    fun playNextAutomix(item: MediaItem, index: Int = 0) {}
    fun addToQueueAutomix(item: MediaItem, index: Int = 0) {}
    fun clearAutomix() {}
}

interface PlayerConnection {
    val player: Player
        get() = Player()
    val service: MusicPlayerService
        get() = object : MusicPlayerService {}
    val mediaMetadata: StateFlow<MediaMetadata?>
        get() = MutableStateFlow(null)
    val currentSong: StateFlow<Song?>
        get() = MutableStateFlow(null)
    val currentLyrics: StateFlow<com.metrolist.music.db.entities.LyricsEntity?>
        get() = MutableStateFlow(null)
    val playbackState: StateFlow<Int>
        get() = MutableStateFlow(Player.STATE_IDLE)
    val isPlaying: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    val isEffectivelyPlaying: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    val canSkipNext: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    val canSkipPrevious: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    val error: StateFlow<PlaybackException?>
        get() = MutableStateFlow(null)
    val repeatMode: StateFlow<Int>
        get() = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val queueWindows: StateFlow<List<Timeline.Window>>
        get() = MutableStateFlow(emptyList())
    val queueTitle: StateFlow<String?>
        get() = MutableStateFlow(null)
    val shuffleModeEnabled: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    val currentFormat: StateFlow<Any?>
        get() = MutableStateFlow(null)
    val currentWindowIndex: StateFlow<Int>
        get() = MutableStateFlow(0)
    val currentMediaItemIndex: StateFlow<Int>
        get() = MutableStateFlow(0)
    val currentStreamClient: StateFlow<String?>
        get() = MutableStateFlow(null)
    val waitingForNetworkConnection: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    var allowInternalSync: Boolean
        get() = true
        set(_) {}

    fun playQueue(queue: Queue) {}
    fun playQueue(queue: Any, index: Int = 0) {}
    fun togglePlayPause() {}
    fun play() {}
    fun pause() {}
    fun addToQueue(item: MediaItem) {}
    fun addToQueue(items: List<MediaItem>) {}
    fun addToQueue(song: SongItem) {}
    fun playNext(item: MediaItem) {}
    fun playNext(items: List<MediaItem>) {}
    fun playNext(song: SongItem) {}
    fun toggleLike() {}
    fun toggleLibrary() {}
    fun toggleMute() {}
    fun toggleRepeatMode() {}
    fun setMuted(muted: Boolean) {}
    fun seekTo(position: Long) {}
    fun seekToNext() {}
    fun seekToPrevious() {}
    fun startRadioSeamlessly() {}
    fun refreshSongMetadata(song: Song) {}
}
