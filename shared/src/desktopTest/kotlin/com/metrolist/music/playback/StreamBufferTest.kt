package com.metrolist.music.playback

import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class StreamBufferTest {
    private val data = Random(7).nextBytes((3.5 * StreamBuffer.BLOCK_SIZE).toInt())
    private lateinit var server: HttpServer
    private val requests = AtomicInteger()
    /** Requests (by number) whose connection is cut halfway, like a network hiccup. */
    private val dropOn = mutableSetOf<Int>()
    private val expireOn = mutableSetOf<Int>()

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/a") { ex ->
                val n = requests.incrementAndGet()
                if (n in expireOn) { ex.sendResponseHeaders(403, -1); ex.close(); return@createContext }
                val (from, to) = ex.requestHeaders.getFirst("Range")!!.removePrefix("bytes=").split("-").map { it.toLong() }
                val end = to.coerceAtMost(data.size - 1L)
                ex.responseHeaders.set("Content-Range", "bytes $from-$end/${data.size}")
                ex.sendResponseHeaders(206, end - from + 1)
                val out = ex.responseBody
                if (n in dropOn) {
                    out.write(data, from.toInt(), ((end - from + 1) / 2).toInt()); out.flush()
                    ex.close(); return@createContext
                }
                out.write(data, from.toInt(), (end - from + 1).toInt()); out.close()
            }
            start()
        }
    }

    @After
    fun stop() = server.stop(0)

    private fun info(contentLength: Long? = data.size.toLong()) = ResolvedStreamInfo(
        videoId = "abcdefghijk", streamUrl = "http://127.0.0.1:${server.address.port}/a", headers = emptyMap(),
        mimeType = "audio/webm", contentLength = contentLength, durationMs = 0, clientName = "test", expiresAtMs = Long.MAX_VALUE,
    )

    private fun buffer(info: ResolvedStreamInfo = info(), refresh: () -> ResolvedStreamInfo? = { null }) =
        StreamBuffer("abcdefghijk", OkHttpClient(), info, refresh, Files.createTempDirectory("sb").toFile())

    @Test
    fun aDroppedConnectionIsRetriedAndTheSongStaysWhole() {
        dropOn += 2
        val b = buffer()
        val out = ByteArrayOutputStream()
        b.copyTo(out, 0)
        assertArrayEquals(data, out.toByteArray())
        assertTrue("a retry happened", requests.get() > 4)
        b.close()
    }

    @Test
    fun aRangeFromTheMiddleIsServed() {
        val b = buffer()
        val from = StreamBuffer.BLOCK_SIZE * 2 + 123
        val to = from + 5000
        val out = ByteArrayOutputStream()
        b.copyTo(out, from, to)
        assertArrayEquals(data.copyOfRange(from.toInt(), to.toInt() + 1), out.toByteArray())
        b.close()
    }

    @Test
    fun theLengthIsProbedWhenUnknown() {
        val b = buffer(info(contentLength = null))
        assertEquals(data.size.toLong(), b.length)
        val out = ByteArrayOutputStream()
        b.copyTo(out, 0)
        assertArrayEquals(data, out.toByteArray())
        b.close()
    }

    @Test
    fun anExpiredUrlIsRefreshedOnce() {
        expireOn += 1
        var refreshed = 0
        val b = buffer(refresh = { refreshed++; info() })
        val out = ByteArrayOutputStream()
        b.copyTo(out, 0)
        assertArrayEquals(data, out.toByteArray())
        assertEquals(1, refreshed)
        b.close()
    }
}
