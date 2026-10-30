package com.metrolist.music.playback

import com.metrolist.music.playback.Timeline
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.music.constants.AudioNormalizationKey
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.constants.AudioQualityKey
import com.metrolist.music.constants.AutoLoadMoreKey
import com.metrolist.music.constants.AutoRadioQueueKey
import com.metrolist.music.constants.AutoSkipNextOnErrorKey
import com.metrolist.music.constants.AutoplayKey
import com.metrolist.music.constants.DisableLoadMoreWhenRepeatAllKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.LoudnessLevel
import com.metrolist.music.constants.LoudnessLevelKey
import com.metrolist.music.constants.PersistentQueueKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Event
import com.metrolist.music.db.entities.FormatEntity
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.platform.AppContext
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.nowLocalDateTime
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.Queue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.playback.queues.filterExplicit
import com.metrolist.music.playback.queues.filterVideoSongs
import com.metrolist.music.utils.InnerTubeXPlayer
import com.metrolist.music.utils.VideoId
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.normalizationGainMb
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.net.InetSocketAddress
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class ResolvedStreamInfo(
    val videoId: String,
    val streamUrl: String,
    val headers: Map<String, String>,
    val mimeType: String,
    val contentLength: Long?,
    val durationMs: Long,
    val clientName: String,
    val expiresAtMs: Long,
    // DESKTOP: loudness metadata used for volume normalization.
    val loudnessDb: Double? = null,
    val perceptualLoudnessDb: Double? = null,
) {
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAtMs - 30_000L
}

/**
 * Local HTTP server VLC streams from: resolves the YouTube stream for a video id on demand and pipes
 * it through with the headers YouTube expects (VLC cannot send them itself).
 */
internal class DesktopStreamProxy(
    private val resolvedStreams: ConcurrentHashMap<String, ResolvedStreamInfo>,
    private val resolve: suspend (videoId: String) -> ResolvedStreamInfo?,
) {
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = Executors.newCachedThreadPool()
        createContext("/stream/") { exchange ->
            handleStreamRequest(exchange)
        }
        start()
    }

    val port: Int get() = server.address.port

    fun getStreamUrl(videoId: String): String = "http://127.0.0.1:$port/stream/$videoId"

    fun stop() {
        runCatching { server.stop(0) }
    }

    private fun handleStreamRequest(exchange: HttpExchange) {
        try {
            val videoId = exchange.requestURI.path.substringAfterLast("/").trim()
            // DESKTOP: only real video ids may hit the resolver behind the local proxy.
            if (!VideoId.isValid(videoId)) {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
                return
            }

            val info = resolvedStreams[videoId]?.takeIf { !it.isExpired() }
                ?: runBlocking(Dispatchers.IO) { resolve(videoId) }
                    ?.also { resolvedStreams[videoId] = it }

            if (info == null) {
                exchange.sendResponseHeaders(502, -1)
                exchange.close()
                return
            }

            val rangeHeader = exchange.requestHeaders.getFirst("Range")
            val reqBuilder = Request.Builder()
                .url(info.streamUrl)
                .get()

            info.headers.forEach { (k, v) ->
                reqBuilder.header(k, v)
            }
            if (!info.headers.keys.any { it.equals("User-Agent", ignoreCase = true) }) {
                reqBuilder.header(
                    "User-Agent",
                    "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                )
            }
            if (!rangeHeader.isNullOrBlank()) {
                reqBuilder.header("Range", rangeHeader)
            }

            httpClient.newCall(reqBuilder.build()).execute().use { upstream ->
                val statusCode = upstream.code
                if (statusCode >= 400) {
                    // DESKTOP: VLC only reports "media player error"; log why the stream was refused
                    // (never the URL: it carries signed tokens) and drop the cached URL so a retry
                    // resolves a fresh one.
                    Timber.w("Stream proxy: upstream HTTP %d for %s (client=%s)", statusCode, videoId, info.clientName)
                    resolvedStreams.remove(videoId)
                }
                val contentType = upstream.header("Content-Type") ?: info.mimeType
                val contentLength = upstream.body?.contentLength() ?: -1L
                val contentRange = upstream.header("Content-Range")

                exchange.responseHeaders.set("Content-Type", contentType)
                exchange.responseHeaders.set("Accept-Ranges", "bytes")
                if (!contentRange.isNullOrBlank()) {
                    exchange.responseHeaders.set("Content-Range", contentRange)
                }

                if (exchange.requestMethod.equals("HEAD", ignoreCase = true)) {
                    exchange.sendResponseHeaders(statusCode, -1)
                    exchange.close()
                    return
                }

                exchange.sendResponseHeaders(statusCode, if (contentLength > 0) contentLength else 0L)
                upstream.body?.byteStream()?.use { input ->
                    exchange.responseBody.use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            output.flush()
                        }
                    }
                }
            }
        } catch (e: java.io.IOException) {
            // Client disconnected (e.g. seek or track change)
        } catch (e: Exception) {
            Timber.w(e, "Stream proxy failed")
        } finally {
            runCatching { exchange.close() }
        }
    }
}
