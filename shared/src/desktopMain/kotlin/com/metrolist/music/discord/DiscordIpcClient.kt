package com.metrolist.music.discord

import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Rich Presence through the locally running Discord app (named pipe discord-ipc-N).
 * Needs no login or network access of its own.
 */
internal class DiscordIpcClient(private val clientId: String) {
    private var pipe: RandomAccessFile? = null
    private var lastAttemptAtMs = 0L
    private var shownSongId: String? = null
    private var shownPlaying: Boolean? = null

    val isConnected: Boolean get() = pipe != null

    /** Connects if Discord is running; retries at most every [RETRY_MS]. */
    @Synchronized
    fun ensureConnected(): Boolean {
        if (pipe != null) return true
        val now = System.currentTimeMillis()
        if (now - lastAttemptAtMs < RETRY_MS) return false
        lastAttemptAtMs = now
        for (i in 0..9) {
            val candidate = runCatching { RandomAccessFile("\\\\.\\pipe\\discord-ipc-$i", "rw") }.getOrNull() ?: continue
            try {
                write(candidate, OP_HANDSHAKE, JSONObject().put("v", 1).put("client_id", clientId))
                val (op, payload) = read(candidate)
                if (op == OP_FRAME && payload.optString("evt") == "READY") {
                    pipe = candidate
                    Timber.tag(TAG).i("ipc: connected via discord-ipc-%d", i)
                    return true
                }
                Timber.tag(TAG).w("ipc: handshake rejected on pipe %d: %s", i, payload)
            } catch (e: Exception) {
                Timber.tag(TAG).d(e, "ipc: pipe %d handshake failed", i)
            }
            runCatching { candidate.close() }
        }
        return false
    }

    @Synchronized
    fun isShowing(songId: String, isPlaying: Boolean): Boolean =
        pipe != null && shownSongId == songId && shownPlaying == isPlaying

    @Synchronized
    fun setActivity(activity: DiscordActivity, songId: String, isPlaying: Boolean) {
        if (send(activityJson(activity))) {
            shownSongId = songId
            shownPlaying = isPlaying
        }
    }

    @Synchronized
    fun clear() {
        if (pipe == null || shownSongId == null) return
        if (send(null)) {
            shownSongId = null
            shownPlaying = null
        }
    }

    @Synchronized
    fun close() {
        runCatching { pipe?.close() }
        pipe = null
        shownSongId = null
        shownPlaying = null
    }

    private fun send(activity: JSONObject?): Boolean {
        val p = pipe ?: return false
        return try {
            val args = JSONObject().put("pid", ProcessHandle.current().pid()).put("activity", activity ?: JSONObject.NULL)
            write(p, OP_FRAME, JSONObject().put("cmd", "SET_ACTIVITY").put("args", args).put("nonce", UUID.randomUUID().toString()))
            val (_, response) = read(p)
            if (response.optString("evt") == "ERROR") {
                Timber.tag(TAG).w("ipc: SET_ACTIVITY error: %s", response.optJSONObject("data"))
                false
            } else {
                true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "ipc: connection lost")
            close()
            false
        }
    }

    private fun activityJson(a: DiscordActivity): JSONObject = JSONObject().apply {
        put("type", a.activityType)
        a.details?.takeIf { it.isNotBlank() }?.let { put("details", it.take(128)) }
        a.state.takeIf { it.isNotBlank() }?.let { put("state", it.take(128)) }
        if (a.startTimestamp > 0L) {
            put(
                "timestamps",
                JSONObject().put("start", a.startTimestamp).apply { a.endTimestamp?.takeIf { it > 0L }?.let { put("end", it) } },
            )
        }
        val assets = JSONObject()
        a.largeImage?.takeIf { it.startsWith("http") }?.let { assets.put("large_image", it) }
        a.largeText?.takeIf { it.isNotBlank() }?.let { assets.put("large_text", it.take(128)) }
        a.smallImage?.takeIf { it.startsWith("http") }?.let { assets.put("small_image", it) }
        a.smallText?.takeIf { it.isNotBlank() }?.let { assets.put("small_text", it.take(128)) }
        if (assets.length() > 0) put("assets", assets)
        val buttons = JSONArray()
        listOf(a.button1Label to a.button1Url, a.button2Label to a.button2Url).forEach { (label, url) ->
            if (!label.isNullOrBlank() && !url.isNullOrBlank()) buttons.put(JSONObject().put("label", label.take(32)).put("url", url))
        }
        if (buttons.length() > 0) put("buttons", buttons)
    }

    private fun write(p: RandomAccessFile, op: Int, payload: JSONObject) {
        val body = payload.toString().toByteArray(Charsets.UTF_8)
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(body.size).array()
        p.write(header + body)
    }

    private fun read(p: RandomAccessFile): Pair<Int, JSONObject> {
        val header = ByteArray(8)
        p.readFully(header)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val op = buf.int
        val length = buf.int
        require(length in 0..MAX_FRAME) { "invalid IPC frame length $length" }
        val body = ByteArray(length)
        p.readFully(body)
        return op to JSONObject(String(body, Charsets.UTF_8))
    }

    private companion object {
        const val TAG = "DiscordSvc"
        const val OP_HANDSHAKE = 0
        const val OP_FRAME = 1
        const val RETRY_MS = 15_000L
        const val MAX_FRAME = 1 shl 20
    }
}
