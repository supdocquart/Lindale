package com.metrolist.music.playback

import com.metrolist.music.eq.EqualizerBackend

/**
 * DESKTOP: the native audio player behind [DesktopPlayerConnection]. Today the only implementation is
 * [VlcEngine] (libVLC); a libmpv engine can be added behind this same interface without touching the
 * queue, streaming or UI code. Listener callbacks arrive on the engine's own thread.
 */
interface AudioEngine {
    interface Listener {
        fun onBuffering()
        fun onPlaying()
        fun onPaused()
        fun onStopped()
        fun onLength(ms: Long)
        fun onEnded()
        fun onError(message: String)
    }

    val name: String
    val equalizer: EqualizerBackend?

    /** Replaces the current media with [url] (a URL or a local path) and starts playing at [startMs]. */
    fun load(url: String, startMs: Long)
    fun play()
    fun pause()
    fun stop()
    fun seek(ms: Long)

    /** 0..200 (100 = unity gain). */
    fun setVolume(percent: Int)

    /**
     * Applies tempo and pitch. Returns false when this engine cannot change them live and has to be
     * recreated with the new values (libVLC sets pitch only at start-up).
     */
    fun setPlaybackParameters(speed: Float, pitch: Float): Boolean

    val hasMedia: Boolean
    val isPlaying: Boolean
    val timeMs: Long
    val lengthMs: Long

    fun release()
}

internal fun isVarispeed(speed: Float, pitch: Float) = speed != 1f && kotlin.math.abs(pitch - speed) < 0.001f

internal fun semitones(pitch: Float): Float = (12.0 * kotlin.math.log2(pitch.toDouble())).toFloat()
