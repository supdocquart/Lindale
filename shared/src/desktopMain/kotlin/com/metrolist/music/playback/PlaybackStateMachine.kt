package com.metrolist.music.playback

/**
 * DESKTOP: the states playback can be in. [DesktopPlayerConnection] changes them only through its
 * moveTo(), which sets playbackState, isPlaying, playWhenReady and error together from [flags], so
 * the flows the UI, Discord, the media session and the mini player read never disagree.
 */
internal sealed interface PlaybackPhase {
    /** Nothing loaded, or stopped by the user. */
    data object Idle : PlaybackPhase

    /** A song is being loaded (stream resolved, engine buffering). */
    data object Buffering : PlaybackPhase

    data object Playing : PlaybackPhase

    /** A song is loaded and paused: by the user, the sleep timer, muting, or restored at start-up. */
    data object Paused : PlaybackPhase

    /** The engine dropped its media while the next song is put in; the user's play intent is kept. */
    data object Stopped : PlaybackPhase

    /** The queue played to the end and autoplay did not continue it. */
    data object Ended : PlaybackPhase

    data class Failed(val error: PlaybackException) : PlaybackPhase
}

/** What a phase means for the player's flows; `null` leaves that value as it is. */
internal data class PlaybackFlags(
    val state: Int?,
    val isPlaying: Boolean?,
    val playWhenReady: Boolean?,
    val error: PlaybackException?,
    /** The engine's own stop after an error must not hide that error. */
    val keepsError: Boolean = false,
)

internal fun PlaybackPhase.flags(): PlaybackFlags = when (this) {
    PlaybackPhase.Idle -> PlaybackFlags(Player.STATE_IDLE, isPlaying = false, playWhenReady = false, error = null)
    // Between two songs the play button keeps its look; the engine reports Playing or Paused next.
    PlaybackPhase.Buffering -> PlaybackFlags(Player.STATE_BUFFERING, isPlaying = null, playWhenReady = null, error = null)
    PlaybackPhase.Playing -> PlaybackFlags(Player.STATE_READY, isPlaying = true, playWhenReady = true, error = null)
    PlaybackPhase.Paused -> PlaybackFlags(Player.STATE_READY, isPlaying = false, playWhenReady = false, error = null)
    PlaybackPhase.Stopped -> PlaybackFlags(null, isPlaying = false, playWhenReady = null, error = null, keepsError = true)
    PlaybackPhase.Ended -> PlaybackFlags(Player.STATE_ENDED, isPlaying = false, playWhenReady = false, error = null)
    is PlaybackPhase.Failed -> PlaybackFlags(Player.STATE_IDLE, isPlaying = false, playWhenReady = null, error = error)
}

/**
 * DESKTOP: numbers every song start. A stream that resolves after the user already moved on, and the
 * engine's "ended" or "error" for media that is no longer the current song, are recognised by their
 * old number and dropped (before, they could load over the new song or skip one song too many).
 */
internal class PlayGenerations {
    @Volatile
    var current = 0L
        private set

    @Volatile
    var loaded = -1L
        private set

    /** A new song was asked for; returns its number. */
    @Synchronized
    fun next(): Long = ++current

    /** Call right before handing [generation]'s media to the engine; false if a newer song was asked for. */
    @Synchronized
    fun load(generation: Long): Boolean {
        if (generation != current) return false
        loaded = generation
        return true
    }

    fun isCurrent(generation: Long): Boolean = generation == current

    /** True when the media in the engine belongs to the song asked for last. */
    fun loadedIsCurrent(): Boolean = loaded == current
}

/**
 * DESKTOP: Player settings > "Pause music when media is muted". Remembers that muting paused the
 * music, so unmuting resumes it; any play or pause by the user (or a new song) forgets it, so unmuting
 * never starts music the user has paused.
 */
internal class MutePause {
    @Volatile
    var pausedByMute = false
        private set

    /** Returns true when the music should be paused now. */
    fun onMute(enabled: Boolean, playing: Boolean): Boolean {
        if (!enabled || !playing) return false
        pausedByMute = true
        return true
    }

    /** Returns true when the music should resume now. */
    fun onUnmute(): Boolean {
        val resume = pausedByMute
        pausedByMute = false
        return resume
    }

    fun onUserAction() {
        pausedByMute = false
    }
}
