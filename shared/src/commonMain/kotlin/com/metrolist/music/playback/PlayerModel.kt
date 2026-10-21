package com.metrolist.music.playback
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

class Bundle {
    fun putString(k: String, v: String) {}
}

class MediaItem(
    val mediaId: String = "",
    val mediaMetadata: MediaMetadata = MediaMetadata(),
    val localConfiguration: LocalConfiguration? = null,
) {
    class LocalConfiguration(
        val tag: Any? = null,
        val uri: Any? = null,
        val customCacheKey: String? = null,
    )
    class Builder {
        private var id: String = ""
        private var uri: Any? = null
        private var customCacheKey: String? = null
        private var tag: Any? = null
        private var metadata: MediaMetadata = MediaMetadata()
        fun setMediaId(id: String): Builder { this.id = id; return this }
        fun setUri(uri: Any): Builder { this.uri = uri; return this }
        fun setCustomCacheKey(key: String): Builder { this.customCacheKey = key; return this }
        fun setTag(tag: Any?): Builder { this.tag = tag; return this }
        fun setMediaMetadata(metadata: MediaMetadata): Builder { this.metadata = metadata; return this }
        fun build(): MediaItem = MediaItem(id, metadata, LocalConfiguration(tag, uri, customCacheKey))
    }
    constructor(localConfiguration: LocalConfiguration?) : this(
        mediaId = "",
        mediaMetadata = MediaMetadata(),
        localConfiguration = localConfiguration,
    )
    fun buildUpon(): Builder = Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(mediaMetadata)
        .apply {
            localConfiguration?.uri?.let { setUri(it) }
            localConfiguration?.customCacheKey?.let { setCustomCacheKey(it) }
            setTag(localConfiguration?.tag)
        }
}

class MediaMetadata(
    val title: CharSequence? = null,
    val subtitle: CharSequence? = null,
    val artist: CharSequence? = null,
    val albumTitle: CharSequence? = null,
    val albumArtist: CharSequence? = null,
    val displayTitle: CharSequence? = null,
    val artworkUri: Any? = null,
    val extras: Any? = null,
    val mediaType: Int = MEDIA_TYPE_MUSIC,
    val isBrowsable: Boolean = false,
    val isPlayable: Boolean = true,
) {
    fun buildUpon(): Builder = Builder()
        .setTitle(title?.toString())
        .setSubtitle(subtitle?.toString())
        .setArtist(artist?.toString())
        .setAlbumTitle(albumTitle?.toString())
        .setAlbumArtist(albumArtist?.toString())
        .setDisplayTitle(displayTitle?.toString())
        .setArtworkUri(artworkUri)
        .setMediaType(mediaType)
        .setIsBrowsable(isBrowsable)
        .setIsPlayable(isPlayable)
        .apply { extras?.let { setExtras(it) } }
    
    class Builder {
        private var title: String? = null
        private var subtitle: String? = null
        private var artist: String? = null
        private var artworkUri: Any? = null
        private var albumTitle: String? = null
        private var albumArtist: String? = null
        private var displayTitle: String? = null
        private var mediaType: Int = MEDIA_TYPE_MUSIC
        private var isBrowsable: Boolean = false
        private var isPlayable: Boolean = true
        private var extras: Any? = null

        fun setTitle(title: String?): Builder { this.title = title; return this }
        fun setSubtitle(subtitle: String?): Builder { this.subtitle = subtitle; return this }
        fun setArtist(artist: String?): Builder { this.artist = artist; return this }
        fun setArtworkUri(uri: Any?): Builder { this.artworkUri = uri; return this }
        fun setAlbumTitle(album: String?): Builder { this.albumTitle = album; return this }
        fun setAlbumArtist(album: String?): Builder { this.albumArtist = album; return this }
        fun setDisplayTitle(title: String?): Builder { this.displayTitle = title; return this }
        fun setMediaType(type: Int): Builder { this.mediaType = type; return this }
        fun setIsBrowsable(v: Boolean): Builder { this.isBrowsable = v; return this }
        fun setIsPlayable(v: Boolean): Builder { this.isPlayable = v; return this }
        fun setExtras(extras: Any): Builder { this.extras = extras; return this }
        fun build(): MediaMetadata = MediaMetadata(
            title = title,
            subtitle = subtitle,
            artist = artist,
            albumTitle = albumTitle,
            albumArtist = albumArtist,
            displayTitle = displayTitle,
            artworkUri = artworkUri,
            extras = extras,
            mediaType = mediaType,
            isBrowsable = isBrowsable,
            isPlayable = isPlayable,
        )
    }
    
    companion object {
        const val MEDIA_TYPE_MUSIC = 1
    }
}

open class Player {
    open val playbackState: Int = STATE_IDLE
    open var playWhenReady: Boolean = false
    open val currentPosition: Long = 0L
    open val duration: Long = 0L
    open val repeatMode: Int = REPEAT_MODE_OFF
    open var shuffleModeEnabled: Boolean = false
    open val currentMediaItem: MediaItem? = null
    open val currentTimeline: Timeline = Timeline()
    open val currentMediaItemIndex: Int = 0
    open val mediaItemCount: Int = 0
    open val isPlaying: Boolean = false
    open val isCurrentMediaItemDynamic: Boolean = false
    open val isCurrentMediaItemSeekable: Boolean = false
    open val previousMediaItemIndex: Int get() = -1
    open val nextMediaItemIndex: Int get() = -1

    open fun play() {}
    open fun pause() {}
    open fun seekTo(position: Long) {}
    open fun seekTo(mediaItemIndex: Int, positionMs: Long) {}
    open fun seekToDefaultPosition(mediaItemIndex: Int) {}
    open fun seekToNext() {}
    open fun seekToPrevious() {}
    open fun seekToPreviousMediaItem() {}
    open fun seekToNextMediaItem() {}
    open fun setRepeatMode(repeatMode: Int) {}
    open fun getMediaItemAt(index: Int): MediaItem = MediaItem()
    open fun addMediaItem(mediaItem: MediaItem) {}
    open fun addMediaItem(index: Int, mediaItem: MediaItem) {}
    open fun addMediaItems(mediaItems: List<MediaItem>) {}
    open fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {}
    open fun moveMediaItem(currentIndex: Int, newIndex: Int) {}
    open fun removeMediaItem(index: Int) {}
    open fun removeMediaItems(fromIndex: Int, toIndex: Int) {}
    open fun clearMediaItems() {}
    open fun prepare() {}
    open fun stop() {}
    open fun release() {}
    open fun isCommandAvailable(command: Int): Boolean = true
    open fun addListener(listener: Any?) {}
    open fun removeListener(listener: Any?) {}

    class Commands {
        fun contains(command: Int): Boolean = true
    }
    open val availableCommands: Commands = Commands()
    open var playbackParameters: PlaybackParameters = PlaybackParameters.DEFAULT
    open var audioSessionId: Int = 0
    open fun setShuffleOrder(shuffleOrder: Any?) {}
    open fun toggleRepeatMode() {
        val next = when (repeatMode) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_ONE
            else -> REPEAT_MODE_OFF
        }
        setRepeatMode(next)
    }

    interface Listener {
        fun onPlaybackStateChanged(playbackState: Int) {}
        fun onIsPlayingChanged(isPlaying: Boolean) {}
        fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {}
        fun onTimelineChanged(timeline: Timeline, reason: Int) {}
        fun onPositionDiscontinuity(oldPosition: Any?, newPosition: Any?, reason: Int) {}
    }

    companion object {
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4

        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1
        const val REPEAT_MODE_ALL = 2

        const val COMMAND_SEEK_TO_MEDIA_ITEM = 10
        const val COMMAND_SEEK_TO_PREVIOUS = 7
        const val COMMAND_SEEK_TO_NEXT = 9
        const val COMMAND_PLAY_PAUSE = 1
        const val COMMAND_SET_SHUFFLE_MODE = 14
        const val COMMAND_SET_REPEAT_MODE = 15
        const val COMMAND_CHANGE_MEDIA_ITEMS = 20
        const val AUDIO_SESSION_ID_UNSET = 0
    }
}

const val AUDIO_SESSION_ID_UNSET = 0

open class Timeline(
    private val windows: List<Window> = emptyList()
) {
    class Window(
        var uid: Any = Any(),
        var mediaItem: MediaItem = MediaItem(),
        var firstPeriodIndex: Int = 0,
        var lastPeriodIndex: Int = 0,
    )

    open val windowCount: Int get() = windows.size
    open fun getWindow(windowIndex: Int, window: Window): Window {
        val src = windows.getOrNull(windowIndex) ?: return window
        window.uid = src.uid
        window.mediaItem = src.mediaItem
        window.firstPeriodIndex = src.firstPeriodIndex
        window.lastPeriodIndex = src.lastPeriodIndex
        return window
    }
    open val isEmpty: Boolean get() = windowCount == 0
    open fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
        if (isEmpty) return -1
        return if (windowIndex > 0) windowIndex - 1 else if (repeatMode == Player.REPEAT_MODE_ALL) windowCount - 1 else -1
    }
    open fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
        if (isEmpty) return -1
        return if (windowIndex + 1 < windowCount) windowIndex + 1 else if (repeatMode == Player.REPEAT_MODE_ALL) 0 else -1
    }
}

fun Timeline.getWindowCount(): Int = windowCount

interface ShuffleOrder {
    class DefaultShuffleOrder(val length: Int = 0, val seed: Long = 0L) : ShuffleOrder {
        constructor(shuffledIndices: IntArray, randomSeed: Long = 0L) : this(shuffledIndices.size, randomSeed)
    }
}
class TrackSelectionParameters

object C {
    const val TIME_UNSET = -9223372036854775807L
    const val AUDIO_SESSION_ID_UNSET = 0
    const val INDEX_UNSET = -1
}

class PlaybackParameters(val speed: Float = 1f, val pitch: Float = 1f) {
    fun withSpeed(speed: Float): PlaybackParameters = PlaybackParameters(speed, pitch)
    companion object {
        val DEFAULT = PlaybackParameters(1f, 1f)
    }
}

class PlaybackException(
    val errorCode: Int = 0,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause) {
    val errorCodeName: String get() = "ERROR_CODE_UNKNOWN"
    companion object {
        const val ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001
        const val ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002
        const val ERROR_CODE_IO_BAD_HTTP_STATUS = 2004
        const val ERROR_CODE_PARSING_CONTAINER_MALFORMED = 3001
        const val ERROR_CODE_DECODER_INIT_FAILED = 4001
        const val ERROR_CODE_BEHIND_LIVE_WINDOW = 1002
        const val ERROR_CODE_TIMEOUT = 1003
        const val ERROR_CODE_FAILED_RUNTIME_CHECK = 1004
        const val ERROR_CODE_UNSPECIFIED = 1000
        const val ERROR_CODE_REMOTE_ERROR = 1001
    }
}

fun Throwable.causeChain(): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = this
    while (current != null) {
        chain.add(current)
        current = current.cause
    }
    return chain
}
