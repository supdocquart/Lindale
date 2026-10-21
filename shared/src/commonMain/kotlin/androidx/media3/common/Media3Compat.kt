package androidx.media3.common

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

typealias Player = com.metrolist.music.playback.Player
typealias MediaItem = com.metrolist.music.playback.MediaItem
typealias MediaMetadata = com.metrolist.music.playback.MediaMetadata
typealias PlaybackParameters = com.metrolist.music.playback.PlaybackParameters
typealias PlaybackException = com.metrolist.music.playback.PlaybackException
typealias C = com.metrolist.music.playback.C
typealias TrackSelectionParameters = com.metrolist.music.playback.TrackSelectionParameters
