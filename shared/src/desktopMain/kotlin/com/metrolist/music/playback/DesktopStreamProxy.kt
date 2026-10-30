package com.metrolist.music.playback

import com.metrolist.innertube.YouTube
import com.metrolist.music.extensions.metadata
import com.metrolist.music.utils.VideoId
import com.metrolist.music.utils.get
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

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
