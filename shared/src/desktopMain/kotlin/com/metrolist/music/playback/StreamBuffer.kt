package com.metrolist.music.playback

import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.BitSet

/**
 * DESKTOP: downloads one song's audio ahead of playback, in [BLOCK_SIZE] range requests, into a
 * temporary file that VLC reads from through the local proxy.
 *
 * Before, the proxy piped one long-lived request straight to VLC. When googlevideo slowed that
 * connection down, or the network dropped it for a moment, VLC ran out of its 1.5 s buffer and the
 * sound cut in and out. Fetching in bounded ranges is what YouTube's own players and yt-dlp
 * (`http_chunk_size`) do; each block that fails is retried on its own, and the rest of the song keeps
 * downloading while it plays, so a short hiccup never reaches the speakers.
 */
internal class StreamBuffer(
    val videoId: String,
    private val client: OkHttpClient,
    @Volatile private var info: ResolvedStreamInfo,
    /** Resolves a fresh stream URL when googlevideo refuses the current one (expired). */
    private val refresh: () -> ResolvedStreamInfo?,
    cacheDir: File,
) {
    val contentType: String get() = info.mimeType
    val length: Long
    private val blockCount: Int
    private val file: File = File.createTempFile("stream-$videoId-", ".part", cacheDir.apply { mkdirs() })
    private val raf = RandomAccessFile(file, "rw")
    private val have = BitSet()
    private val lock = java.lang.Object()

    @Volatile private var wantedBlock = 0
    @Volatile private var closed = false
    @Volatile private var failure: String? = null
    private val startedAt = System.nanoTime()

    init {
        length = info.contentLength?.takeIf { it > 0 } ?: probeLength()
        blockCount = ((length + BLOCK_SIZE - 1) / BLOCK_SIZE).toInt()
        raf.setLength(length)
        Thread({ downloadLoop() }, "stream-buffer-$videoId").apply { isDaemon = true }.start()
    }

    /** Asks for the first block to learn the total size from Content-Range. */
    private fun probeLength(): Long {
        val request = request(0, BLOCK_SIZE - 1)
        client.newCall(request).execute().use { response ->
            if (response.code >= 400) throw IOException("HTTP ${response.code}")
            val total = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?: response.body?.contentLength()?.takeIf { response.code == 200 && it > 0 }
                ?: throw IOException("no content length")
            val bytes = response.body?.bytes() ?: throw IOException("empty body")
            synchronized(lock) {
                raf.setLength(total)
                raf.seek(0)
                raf.write(bytes, 0, bytes.size.coerceAtMost(BLOCK_SIZE.toInt()))
                if (bytes.size.toLong() >= BLOCK_SIZE.coerceAtMost(total)) have.set(0)
            }
            return total
        }
    }

    private fun request(from: Long, to: Long): Request {
        val builder = Request.Builder().url(info.streamUrl).get()
        info.headers.forEach { (k, v) -> builder.header(k, v) }
        if (!info.headers.keys.any { it.equals("User-Agent", ignoreCase = true) }) {
            builder.header("User-Agent", DEFAULT_USER_AGENT)
        }
        builder.header("Range", "bytes=$from-$to")
        return builder.build()
    }

    private fun nextMissingBlock(): Int? = synchronized(lock) {
        val fromWanted = have.nextClearBit(wantedBlock)
        if (fromWanted < blockCount) return fromWanted
        val fromStart = have.nextClearBit(0)
        if (fromStart < blockCount) fromStart else null
    }

    private fun downloadLoop() {
        var refreshed = false
        while (!closed) {
            val block = nextMissingBlock() ?: break
            val from = block * BLOCK_SIZE
            val to = (from + BLOCK_SIZE).coerceAtMost(length) - 1
            var attempt = 0
            while (!closed) {
                try {
                    fetchBlock(block, from, to)
                    break
                } catch (e: ExpiredException) {
                    // A stream URL lives ~6 hours; a long pause can outlive it. Resolve once more.
                    if (refreshed) return fail("stream refused again after refresh (HTTP ${e.code})")
                    refreshed = true
                    info = refresh() ?: return fail("could not refresh the stream (HTTP ${e.code})")
                    Timber.i("Stream buffer: refreshed the stream URL for %s", videoId)
                } catch (e: IOException) {
                    attempt++
                    if (attempt > MAX_RETRIES) return fail("block $block failed $attempt times: ${e.javaClass.simpleName}")
                    Timber.w("Stream buffer: block %d of %s failed (%s), retry %d", block, videoId, e.javaClass.simpleName, attempt)
                    Thread.sleep(RETRY_DELAY_MS * attempt)
                }
            }
        }
        if (!closed && failure == null) {
            val seconds = (System.nanoTime() - startedAt) / 1e9
            Timber.d("Stream buffer: %s complete, %d KB in %.1f s", videoId, length / 1024, seconds)
        }
    }

    private class ExpiredException(val code: Int) : IOException("HTTP $code")

    private fun fetchBlock(block: Int, from: Long, to: Long) {
        client.newCall(request(from, to)).execute().use { response ->
            when {
                response.code == 403 || response.code == 410 -> throw ExpiredException(response.code)
                response.code >= 400 -> throw IOException("HTTP ${response.code}")
            }
            val input = response.body?.byteStream() ?: throw IOException("empty body")
            val buffer = ByteArray(64 * 1024)
            var position = from
            while (position <= to) {
                val read = input.read(buffer, 0, buffer.size.coerceAtMost((to - position + 1).toInt()))
                if (read == -1) break
                synchronized(lock) {
                    raf.seek(position)
                    raf.write(buffer, 0, read)
                }
                position += read
                if (closed) return
            }
            if (position <= to) throw IOException("short block ($position of $to)")
            synchronized(lock) {
                have.set(block)
                lock.notifyAll()
            }
        }
    }

    private fun fail(reason: String) {
        failure = reason
        Timber.w("Stream buffer: giving up on %s: %s", videoId, reason)
        synchronized(lock) { lock.notifyAll() }
    }

    /**
     * Writes bytes [from]..[to] (inclusive) to [out] as they arrive. Returns normally when done; throws IOException
     * when the player hung up (seek, track change) or the download failed.
     */
    fun copyTo(out: OutputStream, from: Long, to: Long = length - 1) {
        var position = from
        val end = to.coerceAtMost(length - 1) + 1
        val buffer = ByteArray(64 * 1024)
        while (position < end) {
            val block = (position / BLOCK_SIZE).toInt()
            wantedBlock = block
            synchronized(lock) {
                val waitUntil = System.currentTimeMillis() + READ_TIMEOUT_MS
                while (!have.get(block) && !closed && failure == null) {
                    val left = waitUntil - System.currentTimeMillis()
                    if (left <= 0) throw IOException("timed out waiting for block $block")
                    lock.wait(left)
                }
            }
            failure?.let { if (!have.get(block)) throw IOException(it) }
            if (closed) throw IOException("closed")
            val blockEnd = ((block + 1) * BLOCK_SIZE).coerceAtMost(end)
            while (position < blockEnd) {
                val n = synchronized(lock) {
                    raf.seek(position)
                    raf.read(buffer, 0, buffer.size.coerceAtMost((blockEnd - position).toInt()))
                }
                if (n <= 0) throw IOException("read failed at $position")
                out.write(buffer, 0, n)
                position += n
            }
            out.flush()
        }
    }

    fun close() {
        closed = true
        synchronized(lock) { lock.notifyAll() }
        runCatching { raf.close() }
        runCatching { file.delete() }
    }

    companion object {
        /** 1 MiB: the first block arrives fast, and a song is only a few requests. yt-dlp uses 10 MiB. */
        const val BLOCK_SIZE = 1L shl 20
        private const val MAX_RETRIES = 5
        private const val RETRY_DELAY_MS = 700L
        private const val READ_TIMEOUT_MS = 30_000L
        const val DEFAULT_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    }
}
