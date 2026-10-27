package com.metrolist.music.playback

import com.metrolist.music.eq.EqualizerBackend
import com.sun.jna.NativeLibrary
import timber.log.Timber
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File

/** DESKTOP: the previous libVLC (vlcj) audio engine, kept as fallback when libmpv is unavailable. */
class VlcEngine private constructor(
    private val factory: MediaPlayerFactory,
    private val mp: MediaPlayer,
    private val createdSpeed: Float,
    private val createdPitch: Float,
    listener: AudioEngine.Listener,
) : AudioEngine {
    override val name = "vlc"

    @Volatile private var speed = createdSpeed

    override val equalizer: EqualizerBackend? =
        runCatching { VlcEqualizerBackend(factory, mp) }.onFailure { Timber.e(it, "EQ: VLC equalizer unavailable") }.getOrNull()

    init {
        mp.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                if (newCache < 100f && !mediaPlayer.status().isPlaying) listener.onBuffering()
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                // VLC resets the rate for each new media; keep the user's tempo.
                if (speed != 1f) runCatching { mediaPlayer.controls().setRate(speed) }
                listener.onPlaying()
            }

            override fun paused(mediaPlayer: MediaPlayer) = listener.onPaused()

            override fun stopped(mediaPlayer: MediaPlayer) = listener.onStopped()

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                if (newLength > 0L) listener.onLength(newLength)
            }

            override fun finished(mediaPlayer: MediaPlayer) = listener.onEnded()

            override fun error(mediaPlayer: MediaPlayer) = listener.onError("VLC playback error")
        })
    }

    override fun load(url: String, startMs: Long) {
        val options = if (startMs > 0L) arrayOf(":start-time=${startMs / 1000.0}") else emptyArray()
        mp.media().play(url, *options)
    }

    override fun play() { mp.controls().play() }

    override fun pause() { mp.controls().pause() }

    override fun stop() { mp.controls().stop() }

    override fun seek(ms: Long) { mp.controls().setTime(ms) }

    override fun setVolume(percent: Int) { mp.audio().setVolume(percent.coerceIn(0, 200)) }

    override fun setPlaybackParameters(speed: Float, pitch: Float): Boolean {
        val pitchChanged = isVarispeed(createdSpeed, createdPitch) != isVarispeed(speed, pitch) ||
            (!isVarispeed(speed, pitch) && kotlin.math.abs(semitones(createdPitch) - semitones(pitch)) > 0.01f)
        if (pitchChanged) return false
        this.speed = speed
        runCatching { mp.controls().setRate(speed) }
        return true
    }

    override val hasMedia: Boolean get() = runCatching { mp.media().isValid }.getOrDefault(false)
    override val isPlaying: Boolean get() = runCatching { mp.status().isPlaying }.getOrDefault(false)
    override val timeMs: Long get() = runCatching { mp.status().time() }.getOrDefault(-1L)
    override val lengthMs: Long get() = runCatching { mp.status().length() }.getOrDefault(-1L)

    override fun release() {
        runCatching { mp.controls().stop() }
        runCatching { mp.release() }
        runCatching { factory.release() }
    }

    companion object {
        private fun args(speed: Float, pitch: Float): Array<String> = buildList {
            add("--no-video")
            add("--no-stats")
            add("--network-caching=1500")
            add("--http-reconnect")
            // Pitch can only be set when libVLC starts, so a pitch change recreates the engine.
            if (isVarispeed(speed, pitch)) {
                add("--no-audio-time-stretch")
            } else if (kotlin.math.abs(semitones(pitch)) > 0.01f) {
                add("--audio-filter=scaletempo_pitch")
                add("--pitch-shift=${semitones(pitch)}")
            }
        }.toTypedArray()

        fun create(listener: AudioEngine.Listener, speed: Float, pitch: Float): VlcEngine? = runCatching {
            val localAppData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            val vlcBase = File(localAppData, "${com.metrolist.music.platform.AppBrand.NAME}/vlc")
            val candidateDirs = buildList {
                // Packaged builds ship VLC next to the app (see paketle.bat).
                System.getProperty("compose.application.resources.dir")?.let { add(File(it, "vlc")) }
                add(File(vlcBase, "vlc-3.0.24"))
                if (vlcBase.exists()) vlcBase.listFiles()?.filter { it.isDirectory }?.forEach { add(it) }
                add(File("C:\\Program Files\\VideoLAN\\VLC"))
            }
            val vlcDir = candidateDirs.firstOrNull { File(it, "libvlc.dll").exists() }
            if (vlcDir != null) {
                val path = vlcDir.absolutePath
                System.setProperty("jna.library.path", path)
                System.setProperty("VLC_PLUGIN_PATH", File(vlcDir, "plugins").absolutePath)
                NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), path)
                NativeLibrary.addSearchPath("libvlccore", path)
                Timber.d("Loaded 64-bit libvlc from: $path")
            }
            val factory = MediaPlayerFactory(*args(speed, pitch))
            val mp = factory.mediaPlayers().newMediaPlayer()
            VlcEngine(factory, mp, speed, pitch, listener).also { it.speed = speed }
        }.onFailure { Timber.e(it, "Failed to initialize VLCJ audio engine") }.getOrNull()
    }
}
