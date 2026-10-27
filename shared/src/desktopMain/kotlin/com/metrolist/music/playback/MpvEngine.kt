package com.metrolist.music.playback

import com.metrolist.music.eq.EqualizerBackend
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import timber.log.Timber
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

/** DESKTOP: the libmpv client API calls we use (see mpv/client.h). */
@Suppress("FunctionName")
internal interface MpvLib : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_request_log_messages(ctx: Pointer, minLevel: String): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer?
    fun mpv_wakeup(ctx: Pointer)
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_error_string(error: Int): String?
}

/**
 * DESKTOP: audio engine on libmpv (one `libmpv-2.dll`, shipped in app resources `mpv/`). Compared to
 * libVLC it starts faster (no plugin folder to scan), changes pitch live and keeps tempo changes
 * artefact-free (scaletempo2 / rubberband).
 */
class MpvEngine private constructor(
    private val lib: MpvLib,
    private val ctx: Pointer,
    private val listener: AudioEngine.Listener,
) : AudioEngine {
    override val name = "mpv"

    @Volatile private var running = true
    @Volatile private var fileLoaded = false
    @Volatile private var paused = false
    @Volatile private var pausedForCache = false
    @Volatile private var pitchFilter = ""
    @Volatile private var eqFilter = ""

    private val eventThread = thread(name = "mpv-events", isDaemon = true) { eventLoop() }

    override val equalizer: EqualizerBackend = object : EqualizerBackend {
        // Same ten bands as the libVLC equaliser, so saved profiles map one to one.
        override val bandFrequencies = listOf(60f, 170f, 310f, 600f, 1000f, 3000f, 6000f, 12000f, 14000f, 16000f)
        override val maxGainDb = 20f

        override fun apply(preampDb: Float, gainsDb: List<Float>) {
            val bands = bandFrequencies.mapIndexed { i, f ->
                val g = gainsDb.getOrElse(i) { 0f }
                String.format(Locale.ROOT, "equalizer=f=%.0f:t=o:w=1:g=%.2f", f, g)
            }
            val pre = String.format(Locale.ROOT, "volume=%.2fdB", preampDb)
            eqFilter = "@lindaleeq:lavfi=[" + (bands + pre).joinToString(",") + "]"
            applyFilters()
        }

        override fun disable() {
            eqFilter = ""
            applyFilters()
        }
    }

    private fun applyFilters() {
        val chain = listOf(pitchFilter, eqFilter).filter { it.isNotEmpty() }.joinToString(",")
        val rc = lib.mpv_set_property_string(ctx, "af", chain)
        if (rc < 0) Timber.w("mpv: af=%s failed: %s", chain, lib.mpv_error_string(rc))
    }

    private fun command(vararg args: String) {
        val rc = lib.mpv_command(ctx, arrayOf(*args, null))
        if (rc < 0) Timber.w("mpv: %s failed: %s", args.firstOrNull(), lib.mpv_error_string(rc))
    }

    private fun setProperty(name: String, value: String) {
        val rc = lib.mpv_set_property_string(ctx, name, value)
        if (rc < 0) Timber.w("mpv: %s=%s failed: %s", name, value, lib.mpv_error_string(rc))
    }

    private fun doubleProperty(name: String): Double? {
        val mem = Memory(8)
        return if (lib.mpv_get_property(ctx, name, FORMAT_DOUBLE, mem) >= 0) mem.getDouble(0) else null
    }

    override fun load(url: String, startMs: Long) {
        paused = false
        setProperty("pause", "no")
        if (startMs > 0L) {
            command("loadfile", url, "replace", "-1", String.format(Locale.ROOT, "start=%.3f", startMs / 1000.0))
        } else {
            command("loadfile", url, "replace")
        }
    }

    override fun play() = setProperty("pause", "no")

    override fun pause() = setProperty("pause", "yes")

    override fun stop() = command("stop")

    override fun seek(ms: Long) = command("seek", String.format(Locale.ROOT, "%.3f", ms / 1000.0), "absolute")

    override fun setVolume(percent: Int) = setProperty("volume", percent.coerceIn(0, 200).toString())

    override fun setPlaybackParameters(speed: Float, pitch: Float): Boolean {
        if (isVarispeed(speed, pitch)) {
            // Pitch follows speed, like a record: just resample.
            setProperty("audio-pitch-correction", "no")
            pitchFilter = ""
        } else {
            setProperty("audio-pitch-correction", "yes")
            pitchFilter = if (kotlin.math.abs(pitch - 1f) > 0.001f) {
                String.format(Locale.ROOT, "@lindalepitch:rubberband=pitch-scale=%.5f", pitch)
            } else {
                ""
            }
        }
        applyFilters()
        setProperty("speed", String.format(Locale.ROOT, "%.4f", speed))
        return true
    }

    override val hasMedia: Boolean get() = fileLoaded
    override val isPlaying: Boolean get() = fileLoaded && !paused
    override val timeMs: Long get() = doubleProperty("time-pos")?.let { (it * 1000).toLong() } ?: -1L
    override val lengthMs: Long get() = doubleProperty("duration")?.let { (it * 1000).toLong() } ?: -1L

    override fun release() {
        running = false
        lib.mpv_wakeup(ctx)
        runCatching { eventThread.join(2_000) }
        lib.mpv_terminate_destroy(ctx)
    }

    private fun eventLoop() {
        while (running) {
            val ev = lib.mpv_wait_event(ctx, 1.0) ?: continue
            val id = ev.getInt(0)
            if (id == EVENT_NONE) continue
            if (id == EVENT_SHUTDOWN) break
            runCatching { handle(id, ev) }.onFailure { Timber.e(it, "mpv: event %d", id) }
        }
    }

    private fun handle(id: Int, ev: Pointer) {
        val data = ev.getPointer(16)
        when (id) {
            EVENT_FILE_LOADED -> fileLoaded = true
            EVENT_PLAYBACK_RESTART -> if (!paused) listener.onPlaying()
            EVENT_END_FILE -> {
                val reason = data?.getInt(0) ?: -1
                val error = data?.getInt(4) ?: 0
                if (reason == END_REDIRECT) return
                fileLoaded = false
                when (reason) {
                    END_EOF -> listener.onEnded()
                    END_ERROR -> listener.onError(lib.mpv_error_string(error) ?: "error $error")
                    else -> listener.onStopped()
                }
            }
            EVENT_PROPERTY_CHANGE -> {
                if (data == null) return
                val name = data.getPointer(0)?.getString(0, "UTF-8") ?: return
                val format = data.getInt(8)
                val value = data.getPointer(16)
                when (name) {
                    "pause" -> if (format == FORMAT_FLAG && value != null) {
                        paused = value.getInt(0) != 0
                        if (fileLoaded) if (paused) listener.onPaused() else listener.onPlaying()
                    }
                    "paused-for-cache" -> if (format == FORMAT_FLAG && value != null) {
                        val now = value.getInt(0) != 0
                        if (now && !pausedForCache) listener.onBuffering()
                        if (!now && pausedForCache && fileLoaded && !paused) listener.onPlaying()
                        pausedForCache = now
                    }
                    "duration" -> if (format == FORMAT_DOUBLE && value != null) {
                        listener.onLength((value.getDouble(0) * 1000).toLong())
                    }
                }
            }
            EVENT_LOG_MESSAGE -> if (data != null) {
                val prefix = data.getPointer(0)?.getString(0, "UTF-8").orEmpty()
                val level = data.getPointer(8)?.getString(0, "UTF-8").orEmpty()
                val text = data.getPointer(16)?.getString(0, "UTF-8").orEmpty().trimEnd()
                Timber.tag("mpv").w("[%s] %s: %s", level, prefix, text)
            }
        }
    }

    companion object {
        private const val FORMAT_FLAG = 3
        private const val FORMAT_DOUBLE = 5
        private const val EVENT_NONE = 0
        private const val EVENT_SHUTDOWN = 1
        private const val EVENT_LOG_MESSAGE = 2
        private const val EVENT_END_FILE = 7
        private const val EVENT_FILE_LOADED = 8
        private const val EVENT_PLAYBACK_RESTART = 21
        private const val EVENT_PROPERTY_CHANGE = 22
        private const val END_EOF = 0
        private const val END_ERROR = 4
        private const val END_REDIRECT = 5
        private const val LIB = "libmpv-2"

        /** Folders that may hold libmpv-2.dll: packaged app resources, then dev-tree and user folders. */
        private fun candidateDirs(): List<File> = buildList {
            System.getProperty("compose.application.resources.dir")?.let { add(File(it, "mpv")) }
            System.getProperty("user.dir")?.let { dir ->
                add(File(dir, "desktop/appResources/windows/mpv"))
                add(File(dir, "appResources/windows/mpv"))
            }
            System.getenv("LOCALAPPDATA")?.let { add(File(it, "${com.metrolist.music.platform.AppBrand.NAME}/mpv")) }
        }

        /** Null when libmpv is missing or fails to start; the caller then falls back to libVLC. */
        fun create(listener: AudioEngine.Listener, speed: Float, pitch: Float): MpvEngine? = runCatching {
            val dir = candidateDirs().firstOrNull { File(it, "$LIB.dll").isFile }
                ?: return null.also { Timber.w("mpv: libmpv-2.dll not found") }
            NativeLibrary.addSearchPath(LIB, dir.absolutePath)
            val lib = Native.load(LIB, MpvLib::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
            val ctx = lib.mpv_create() ?: error("mpv_create returned null")
            val options = linkedMapOf(
                "config" to "no",
                "terminal" to "no",
                "load-scripts" to "no",
                "ytdl" to "no",
                "input-default-bindings" to "no",
                "osc" to "no",
                "vid" to "no",
                "video" to "no",
                "audio-display" to "no",
                "idle" to "yes",
                "keep-open" to "no",
                "volume-max" to "200",
                "cache" to "yes",
                "network-timeout" to "20",
                // Shown in the Windows volume mixer instead of a generic name.
                "audio-client-name" to com.metrolist.music.platform.AppBrand.NAME,
            )
            options.forEach { (k, v) ->
                val rc = lib.mpv_set_option_string(ctx, k, v)
                if (rc < 0) Timber.w("mpv: option %s=%s: %s", k, v, lib.mpv_error_string(rc))
            }
            val rc = lib.mpv_initialize(ctx)
            if (rc < 0) {
                lib.mpv_terminate_destroy(ctx)
                error("mpv_initialize: ${lib.mpv_error_string(rc)}")
            }
            lib.mpv_request_log_messages(ctx, "warn")
            lib.mpv_observe_property(ctx, 1, "pause", FORMAT_FLAG)
            lib.mpv_observe_property(ctx, 2, "paused-for-cache", FORMAT_FLAG)
            lib.mpv_observe_property(ctx, 3, "duration", FORMAT_DOUBLE)
            Timber.i("mpv: libmpv loaded from %s", dir.absolutePath)
            MpvEngine(lib, ctx, listener).also { it.setPlaybackParameters(speed, pitch) }
        }.onFailure { Timber.e(it, "mpv: could not start") }.getOrNull()
    }
}
