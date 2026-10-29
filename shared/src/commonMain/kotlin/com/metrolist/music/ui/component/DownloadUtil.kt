package com.metrolist.music.ui.component
import com.metrolist.music.platform.AppBrand
import com.metrolist.music.platform.AppContext

import com.metrolist.music.playback.Download
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.utils.VideoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class DownloadUtil {
    val downloads: StateFlow<Map<String, Download>> get() = DownloadCenter.downloads

    fun getDownload(id: String): StateFlow<Download?> = DownloadCenter.downloadFlow(id)
    fun getDownloadState(id: String): Flow<Int> = DownloadCenter.downloads.map { it[id]?.state ?: Download.STATE_STOPPED }

    fun download(song: Any?) {
        val meta = when (song) {
            is MediaMetadata -> song
            is Song -> song.toMediaMetadata()
            is SongItem -> song.toMediaMetadata()
            else -> null
        } ?: return
        DownloadCenter.enqueue(meta)
    }

    fun removeDownload(id: String) = DownloadCenter.remove(id)
    fun resumeDownload(id: String) = DownloadCenter.retry(id)
}

/**
 * Offline downloads on desktop: streams are resolved by the audio engine, saved under
 * <app data>/downloads/<videoId>.<ext> and the song row is flagged as downloaded.
 */
object DownloadCenter {
    data class StreamSource(
        val url: String,
        val headers: Map<String, String>,
        val mimeType: String,
        val contentLength: Long?,
    )

    interface Engine {
        suspend fun resolve(meta: MediaMetadata): StreamSource
        suspend fun markDownloaded(songId: String, downloaded: Boolean)
    }

    @Volatile
    var engine: Engine? = null

    private const val CHUNK = 4L * 1024 * 1024
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(2)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val pending = ConcurrentHashMap<String, MediaMetadata>()
    private val perId = ConcurrentHashMap<String, MutableStateFlow<Download?>>()

    // DESKTOP: downloads are ordinary files in the user's Music folder, so other players and Explorer see them.
    // shortcut: assumes Music is at %USERPROFILE%\Music; read the known-folder path if OneDrive-redirected users complain.
    val directory: File by lazy {
        File(File(System.getProperty("user.home"), "Music"), AppBrand.NAME).apply {
            mkdirs()
            // Earlier versions kept "<id>.<ext>" files in the app data folder; move them over once.
            File(AppContext().filesDir, "downloads").listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.forEach { old ->
                runCatching { old.copyTo(File(this, old.name), overwrite = false); old.delete() }
                    .onFailure { timber.log.Timber.w(it, "download: could not move %s", old.name) }
            }
        }
    }

    /** "Artist - Title [id].m4a" -> id; older "<id>.m4a" -> id. Also works for ".part" files. */
    private fun idOf(f: File): String {
        val n = f.name.removeSuffix(".part").substringBeforeLast('.')
        return n.substringAfterLast('[', "").substringBefore(']').ifEmpty { n }
    }

    private fun filesOf(id: String): List<File> = directory.listFiles { f -> f.isFile && idOf(f) == id }.orEmpty().toList()

    private fun fileName(meta: MediaMetadata, ext: String): String {
        val label = (meta.artists.joinToString { it.name }.takeIf { it.isNotBlank() }?.let { "$it - " }.orEmpty() + meta.title)
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.').take(120)
        return "$label [${meta.id}].$ext"
    }

    fun totalBytes(): Long = directory.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".part") }.sumOf { it.length() }

    fun removeAll() {
        _downloads.value.keys.toList().forEach(::remove)
    }

    private val _downloads = MutableStateFlow(scanExisting())
    val downloads: StateFlow<Map<String, Download>> = _downloads.asStateFlow()

    private fun scanExisting(): Map<String, Download> =
        directory.listFiles().orEmpty()
            .filter { it.isFile && !it.name.endsWith(".part") && VideoId.isValid(idOf(it)) }
            .associate { idOf(it) to Download(idOf(it), Download.STATE_COMPLETED, 1f) }

    fun localFile(id: String): File? {
        if (!VideoId.isValid(id)) return null
        return filesOf(id).firstOrNull { !it.name.endsWith(".part") }
    }

    fun downloadFlow(id: String): StateFlow<Download?> =
        perId.getOrPut(id) { MutableStateFlow(_downloads.value[id]) }.asStateFlow()

    private fun setState(id: String, download: Download?) {
        _downloads.value = if (download == null) _downloads.value - id else _downloads.value + (id to download)
        perId.getOrPut(id) { MutableStateFlow(null) }.value = download
    }

    fun enqueue(meta: MediaMetadata) {
        val id = meta.id
        // DESKTOP: the id becomes part of a file name in the downloads folder, so reject anything that is not a video id.
        if (!VideoId.isValid(id)) {
            timber.log.Timber.w("download: refusing invalid id (length %d)", id.length)
            return
        }
        if (_downloads.value[id]?.state == Download.STATE_COMPLETED && localFile(id) != null) return
        if (jobs[id]?.isActive == true) return
        pending[id] = meta
        setState(id, Download(id, Download.STATE_QUEUED, 0f))
        jobs[id] = scope.launch {
            permits.withPermit { runDownload(meta) }
            jobs.remove(id)
        }
    }

    fun retry(id: String) {
        pending[id]?.let(::enqueue)
    }

    fun remove(id: String) {
        jobs.remove(id)?.cancel()
        pending.remove(id)
        scope.launch {
            filesOf(id).forEach { it.delete() }
            setState(id, null)
            runCatching {
                engine?.markDownloaded(id, false)
            }.onFailure { timber.log.Timber.w(it, "download: could not clear db flag for %s", id) }
        }
    }

    private suspend fun runDownload(meta: MediaMetadata) {
        val id = meta.id
        // DESKTOP: same guard as enqueue(), right before the file names are built.
        if (!VideoId.isValid(id)) {
            timber.log.Timber.w("download: refusing invalid id (length %d)", id.length)
            setState(id, Download(id, Download.STATE_FAILED, 0f))
            return
        }
        val engine = engine ?: run {
            timber.log.Timber.w("download: engine not ready, cannot download %s", id)
            setState(id, Download(id, Download.STATE_FAILED, 0f))
            return
        }
        try {
            setState(id, Download(id, Download.STATE_DOWNLOADING, 0f))
            val source = engine.resolve(meta)
            val ext = when {
                source.mimeType.contains("mp4") || source.mimeType.contains("m4a") -> "m4a"
                source.mimeType.contains("webm") -> "webm"
                source.mimeType.contains("mpeg") -> "mp3"
                else -> "audio"
            }
            val target = File(directory, fileName(meta, ext))
            val part = File(directory, target.name + ".part")
            filesOf(id).forEach { it.delete() }

            var total = source.contentLength ?: -1L
            var written = 0L
            part.outputStream().buffered().use { out ->
                while (total < 0 || written < total) {
                    currentCoroutineContext().ensureActive()
                    val end = if (total > 0) minOf(written + CHUNK, total) - 1 else written + CHUNK - 1
                    val conn = URI(source.url).toURL().openConnection() as HttpURLConnection
                    try {
                        conn.connectTimeout = 15_000
                        conn.readTimeout = 30_000
                        source.headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                        conn.setRequestProperty("Range", "bytes=$written-$end")
                        val code = conn.responseCode
                        if (code == 416) break
                        check(code == 200 || code == 206) { "HTTP $code" }
                        if (total < 0) {
                            total = conn.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                                ?: conn.contentLengthLong.takeIf { code == 200 } ?: -1L
                        }
                        var read = 0L
                        conn.inputStream.use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                currentCoroutineContext().ensureActive()
                                out.write(buf, 0, n)
                                read += n
                                written += n
                                if (total > 0) setState(id, Download(id, Download.STATE_DOWNLOADING, written.toFloat() / total))
                            }
                        }
                        if (code == 200 || read == 0L) break
                    } finally {
                        conn.disconnect()
                    }
                }
            }
            check(written > 0) { "empty download" }
            target.delete()
            check(part.renameTo(target)) { "could not move ${part.name}" }
            engine.markDownloaded(id, true)
            pending.remove(id)
            setState(id, Download(id, Download.STATE_COMPLETED, 1f))
            timber.log.Timber.i("download: completed %s (%d bytes)", id, written)
        } catch (e: kotlinx.coroutines.CancellationException) {
            filesOf(id).filter { it.name.endsWith(".part") }.forEach { it.delete() }
            throw e
        } catch (e: Throwable) {
            timber.log.Timber.e(e, "download: failed %s", id)
            filesOf(id).filter { it.name.endsWith(".part") }.forEach { it.delete() }
            setState(id, Download(id, Download.STATE_FAILED, 0f))
        }
    }
}
