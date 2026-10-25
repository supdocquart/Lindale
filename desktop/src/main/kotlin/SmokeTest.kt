import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.strategy.PlaybackClientCatalog
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.createDesktopDatabase
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.lyrics.LyricsHelper
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.platform.AppContext
import com.metrolist.music.platform.DesktopFiles
import com.metrolist.music.platform.initDesktopPlatform
import com.metrolist.music.playback.DesktopPlayerConnection
import com.metrolist.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipFile
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Headless smoke test. Run through `gradlew :desktop:smokeTest`, which points metrolist.dataDir at
 * a throwaway build folder. Never run this without that property: main() refuses to start.
 */
private const val SEARCH_QUERY = "Kendrick Lamar Not Like Us"

// Mirrors LyricsEntity.LYRICS_NOT_FOUND; the entity class itself pulls in Room annotations that are
// not on the desktop compile classpath.
private const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"

private const val TOTAL_BUDGET_MS = 280_000L
private const val WATCHDOG_MS = 295_000L

fun main() {
    val dataDirProperty = System.getProperty("metrolist.dataDir")
    if (dataDirProperty.isNullOrBlank()) {
        println("FAIL safety: -Dmetrolist.dataDir is not set; refusing to touch the real app data")
        System.out.flush()
        Runtime.getRuntime().halt(2)
        return
    }

    val smokeDir = try {
        File(dataDirProperty).canonicalFile
    } catch (t: Throwable) {
        println("FAIL safety: invalid metrolist.dataDir '$dataDirProperty': ${t.describe()}")
        System.out.flush()
        Runtime.getRuntime().halt(2)
        return
    }
    if (isRealAppData(smokeDir)) {
        println("FAIL safety: metrolist.dataDir points at the real Metrolist data folder ($smokeDir)")
        System.out.flush()
        Runtime.getRuntime().halt(2)
        return
    }

    installWatchdog()

    val started = System.currentTimeMillis()
    println("SMOKE start dataDir=$smokeDir")
    val exitCode = try {
        runBlocking { SmokeRun(started + TOTAL_BUDGET_MS).execute() }
    } catch (t: Throwable) {
        println("FAIL smoke-run: ${t.describe()}")
        1
    }
    println("SMOKE end (${System.currentTimeMillis() - started} ms)")
    System.out.flush()
    // Halt instead of exit: the desktop player's HTTP proxy uses non-daemon executor threads.
    Runtime.getRuntime().halt(exitCode)
}

private fun isRealAppData(candidate: File): Boolean =
    listOfNotNull(System.getenv("APPDATA"), System.getenv("LOCALAPPDATA"))
        .mapNotNull { root -> runCatching { File(root, "Metrolist").canonicalFile }.getOrNull() }
        .any { real -> candidate == real || candidate.path.startsWith(real.path + File.separator) }

private fun installWatchdog() {
    Thread({
        Thread.sleep(WATCHDOG_MS)
        println("FAIL watchdog: smoke test exceeded ${WATCHDOG_MS / 1000} s; aborting")
        System.out.flush()
        Runtime.getRuntime().halt(3)
    }, "smoke-watchdog").apply { isDaemon = true }.start()
}

private class SmokeRun(private val deadline: Long) {
    private var passed = 0
    private var failed = 0
    private var skipped = 0
    private var database: MusicDatabase? = null
    private var songIds: List<String> = emptyList()

    suspend fun execute(): Int {
        val initStarted = System.currentTimeMillis()
        try {
            initDesktopPlatform()
            YouTube.locale = YouTubeLocale(gl = "US", hl = "en")
            pass("init", initStarted)
        } catch (t: Throwable) {
            fail("init", initStarted, "${t.describe()}; code: initDesktopPlatform()")
        }

        checkSearch()
        checkStream()
        checkLyrics()
        checkSettingsEncryption()
        checkQueuePersistence()
        checkBackup()

        println("SMOKE summary: $passed passed, $failed failed, $skipped skipped")
        return if (failed == 0) 0 else 1
    }

    // 1. Search ---------------------------------------------------------------------------------

    private suspend fun checkSearch() {
        val started = System.currentTimeMillis()
        val budget = budget(60_000L)
        if (budget <= 0L) {
            fail("search", started, "global time budget exhausted; code: SmokeTest.checkSearch")
            return
        }

        if (YouTube.visitorData.isNullOrBlank()) {
            attempt { YouTube.visitorData() }.getOrNull()?.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { YouTube.visitorData = it }
        }

        val outcome = withTimeoutOrNull(budget) {
            attempt {
                YouTube.search(SEARCH_QUERY, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
                    .items
                    .filterIsInstance<SongItem>()
            }
        }

        when {
            outcome == null -> fail(
                "search",
                started,
                "timed out after $budget ms; code: innertube YouTube.search()",
            )
            outcome.isFailure -> fail(
                "search",
                started,
                "${outcome.exceptionOrNull()!!.describe()}; code: innertube YouTube.search()",
            )
            else -> {
                val songs = outcome.getOrThrow()
                songIds = songs.map { it.id }.distinct().take(3)
                if (songs.isEmpty()) {
                    fail("search", started, "YouTube returned no songs; code: innertube YouTube.search()")
                } else {
                    pass(
                        "search",
                        started,
                        "query=\"$SEARCH_QUERY\", songs=${songs.size}, videoIds=${songIds.joinToString()}",
                    )
                }
            }
        }
    }

    // 2. Stream ---------------------------------------------------------------------------------

    private suspend fun checkStream() {
        val started = System.currentTimeMillis()
        val budget = budget(120_000L)
        if (budget <= 0L) {
            fail("stream", started, "global time budget exhausted; code: SmokeTest.checkStream")
            return
        }
        if (songIds.size < 3) {
            fail(
                "stream",
                started,
                "needs 3 video ids from the search check, got ${songIds.size}; code: SmokeTest.checkSearch",
            )
            return
        }

        val details = mutableListOf<String>()
        val errors = mutableListOf<String>()
        for (videoId in songIds.take(3)) {
            val perVideoBudget = minOf(35_000L, remaining())
            if (perVideoBudget <= 0L) {
                errors += "$videoId -> global time budget exhausted"
                break
            }
            val outcome = withTimeoutOrNull(perVideoBudget) {
                attempt { httpProbe(resolvePlayableStream(videoId)) }
            }
            when {
                outcome == null -> errors += "$videoId -> timeout after $perVideoBudget ms"
                outcome.isFailure -> errors += "$videoId -> ${outcome.exceptionOrNull()!!.describe()}"
                else -> details += "$videoId -> ${outcome.getOrThrow()}"
            }
        }

        if (errors.isEmpty()) {
            pass("stream", started, *details.toTypedArray())
        } else {
            fail(
                "stream",
                started,
                "${errors.joinToString("; ")}; code: InnerTubeXPlayer.playerResponseForPlayback() / " +
                    "DesktopPlayerConnection.resolveStream() fallback",
            )
        }
    }

    private suspend fun resolvePlayableStream(videoId: String): PlayableStream {
        val innerTubeX = InnerTubeXPlayer.playerResponseForPlayback(
            videoId = videoId,
            audioQuality = AudioQuality.AUTO,
            connectivityManager = Any(),
            contentHints = ContentHints(),
        )
        innerTubeX.getOrNull()?.let { data ->
            return PlayableStream(data.streamUrl, data.streamHeaders, data.streamClient)
        }

        InnerTubeXPlayer.clearStreamClientFailures()

        // Same client list/order as DesktopPlayerConnection.resolveStream fallback.
        val catalogIds = listOf(
            "ANDROID_VR_NO_AUTH",
            "ANDROID_VR_1_65_10",
            "TVHTML5",
            "TVHTML5_DOWNGRADED",
            "TVHTML5_SIMPLY",
            "VISIONOS_0_1",
            "VISIONOS",
            "IOS",
            "IPADOS",
            "ANDROID",
        )
        val fallbackClients = buildList {
            catalogIds.forEach { id ->
                PlaybackClientCatalog.findManifest(id)?.client?.let(::add)
            }
            add(YouTubeClient.TVHTML5)
            add(YouTubeClient.TVHTML5_DOWNGRADED)
            add(YouTubeClient.ANDROID_VR_NO_AUTH)
            add(YouTubeClient.ANDROID_VR_1_65_10)
            add(YouTubeClient.IOS)
            add(YouTubeClient.WEB_REMIX)
            add(YouTubeClient.WEB)
        }.distinctBy { "${it.clientName}:${it.clientVersion}" }

        val failures = mutableListOf<String>()
        for (client in fallbackClients) {
            val response = YouTube.player(videoId, client = client).getOrNull() ?: continue
            val audioFormats =
                (response.streamingData?.adaptiveFormats.orEmpty() + response.streamingData?.formats.orEmpty())
                    .filter { it.mimeType.startsWith("audio/") }
            if (audioFormats.isEmpty()) {
                failures += "${client.clientName}: no audio formats"
                continue
            }

            val directFormat = audioFormats.filter { !it.url.isNullOrBlank() }.maxByOrNull { it.bitrate }
            val cipherFormat = audioFormats.firstOrNull {
                !it.signatureCipher.isNullOrBlank() || !it.cipher.isNullOrBlank()
            }
            val bestFormat = directFormat ?: cipherFormat
            if (bestFormat == null) {
                failures += "${client.clientName}: no playable format"
                continue
            }

            var url = bestFormat.url
            if (url.isNullOrBlank()) {
                val cipher = bestFormat.signatureCipher ?: bestFormat.cipher
                if (!cipher.isNullOrBlank()) url = InnerTubeXPlayer.deobfuscateCipher(videoId, cipher)
            }
            if (url.isNullOrBlank()) {
                failures += "${client.clientName}: empty URL"
                continue
            }
            return PlayableStream(
                url = url,
                headers = mapOf("User-Agent" to client.userAgent),
                client = client.clientName,
            )
        }

        val innerTubeXError = innerTubeX.exceptionOrNull()?.describe() ?: "no stream"
        throw IOException(
            "InnerTubeX: $innerTubeXError; fallback clients: ${failures.take(4).joinToString()}",
        )
    }

    private fun httpProbe(stream: PlayableStream): String {
        val connection = URI.create(stream.url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            for ((key, value) in stream.headers) {
                if (key.equals("Range", true) || key.equals("Host", true) ||
                    key.equals("Content-Length", true) || key.equals("Accept-Encoding", true)
                ) {
                    continue
                }
                connection.setRequestProperty(key, value)
            }
            if (connection.getRequestProperty("User-Agent").isNullOrBlank()) {
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            connection.setRequestProperty("Range", "bytes=0-65535")

            val code = connection.responseCode
            if (code != 200 && code != 206) {
                val body = connection.errorStream?.use { it.readNBytes(256) }?.decodeToString().orEmpty()
                throw IOException("HTTP $code ${connection.responseMessage} ${body.take(120)}".trim())
            }
            val contentType = connection.contentType.orEmpty()
            val bytes = connection.inputStream.use { it.readNBytes(65_536) }
            if (bytes.size < 1024) {
                throw IOException("only ${bytes.size} bytes (HTTP $code)")
            }
            if (!contentType.contains("audio", ignoreCase = true) && !looksLikeAudio(bytes)) {
                throw IOException(
                    "not audio bytes (HTTP $code, type=${contentType.ifBlank { "unknown" }}, ${bytes.size} bytes)",
                )
            }
            return "HTTP $code, ${bytes.size} bytes, ${contentType.ifBlank { "audio" }}, client=${stream.client}"
        } finally {
            connection.disconnect()
        }
    }

    private fun looksLikeAudio(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        fun tag(offset: Int, value: String): Boolean =
            bytes.size >= offset + value.length &&
                value.indices.all { bytes[offset + it] == value[it].code.toByte() }

        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        return (b0 == 0x1A && b1 == 0x45 && (bytes[2].toInt() and 0xFF) == 0xDF) ||
            tag(4, "ftyp") ||
            tag(0, "OggS") ||
            tag(0, "fLaC") ||
            tag(0, "RIFF") ||
            (b0 == 0xFF && (b1 and 0xE0) == 0xE0)
    }

    // 3. Lyrics ---------------------------------------------------------------------------------

    private suspend fun checkLyrics() {
        val started = System.currentTimeMillis()
        val helper = LyricsHelper(AppContext.Default)
        val songs = listOf(
            Triple("Not Like Us", "Kendrick Lamar", 274),
            Triple("Blinding Lights", "The Weeknd", 200),
            Triple("Bohemian Rhapsody", "Queen", 355),
        )
        val details = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for ((title, artist, duration) in songs) {
            val perSongBudget = minOf(40_000L, remaining())
            if (perSongBudget <= 1000L) {
                errors += "$title -> global time budget exhausted"
                continue
            }
            val outcome = withTimeoutOrNull(perSongBudget) {
                attempt {
                    helper.getLyrics(
                        smokeMetadata(
                            id = "smoke-${title.lowercase().replace(' ', '-')}",
                            title = title,
                            artist = artist,
                            duration = duration,
                        ),
                    )
                }
            }
            when {
                outcome == null -> errors += "$title -> timeout after $perSongBudget ms"
                outcome.isFailure -> errors += "$title -> ${outcome.exceptionOrNull()!!.describe()}"
                else -> {
                    val found = outcome.getOrThrow()
                    when {
                        found.lyrics.isBlank() ->
                            errors += "$title -> blank lyrics (provider=${found.provider})"
                        found.lyrics == LYRICS_NOT_FOUND ->
                            errors += "$title -> LYRICS_NOT_FOUND (provider=${found.provider})"
                        else -> details += "$title -> ${found.provider} (${found.lyrics.length} chars)"
                    }
                }
            }
        }

        if (errors.isEmpty()) {
            pass("lyrics", started, *details.toTypedArray())
        } else {
            fail(
                "lyrics",
                started,
                "${errors.joinToString("; ")}; code: LyricsHelper.getLyrics()",
            )
        }
    }

    // 4. Settings encryption --------------------------------------------------------------------

    private suspend fun checkSettingsEncryption() {
        val started = System.currentTimeMillis()
        val budget = budget(30_000L)
        if (budget <= 0L) {
            fail("settings-encryption", started, "global time budget exhausted; code: SmokeTest.checkSettingsEncryption")
            return
        }

        val outcome = withTimeoutOrNull(budget) {
            attempt {
                writeSmokePreference()
                val readBack = readSmokePreference()
                if (readBack != false) {
                    throw IllegalStateException("read back '$readBack' instead of false")
                }
                val settingsFile = File(DesktopFiles.dataDir, "datastore/settings.preferences_pb")
                if (!settingsFile.isFile) {
                    throw IllegalStateException("settings file was not created at $settingsFile")
                }
                val magic = settingsFile.inputStream().use { it.readNBytes(8) }
                if (!magic.contentEquals("MLDPAPI1".toByteArray())) {
                    throw IllegalStateException(
                        "file starts with ${magic.joinToString("") { "%02x".format(it) }} instead of MLDPAPI1",
                    )
                }
                settingsFile.length()
            }
        }

        when {
            outcome == null -> fail(
                "settings-encryption",
                started,
                "timed out after $budget ms; code: EncryptedPreferencesSerializer.writeTo()",
            )
            outcome.isFailure -> fail(
                "settings-encryption",
                started,
                "${outcome.exceptionOrNull()!!.describe()}; code: EncryptedPreferencesSerializer.writeTo() " +
                    "(WindowsDpapi.install / SecretProtector backend)",
            )
            else -> pass(
                "settings-encryption",
                started,
                "DataStore round-trip ok, settings.preferences_pb=${outcome.getOrThrow()} bytes, magic=MLDPAPI1",
            )
        }
    }

    // The desktop module does not see androidx.datastore at compile time (shared keeps it internal),
    // so the real DataStore extension is called through its public static entry points.
    private suspend fun writeSmokePreference() {
        val dataStore = dataStoreInstance()
        val keyClass = Class.forName("androidx.datastore.preferences.core.Preferences\$Key")
        val key = smokePreferenceKey()
        val setMethod = Class.forName("androidx.datastore.preferences.core.MutablePreferences")
            .getMethod("set", keyClass, Any::class.java)
        val transform = object : Function2<Any, Continuation<Unit>, Any> {
            override fun invoke(preferences: Any, completion: Continuation<Unit>): Any {
                setMethod.invoke(preferences, key, false)
                return Unit
            }
        }
        val edit = Class.forName("androidx.datastore.preferences.core.PreferencesKt").getMethod(
            "edit",
            Class.forName("androidx.datastore.core.DataStore"),
            Function2::class.java,
            Continuation::class.java,
        )
        invokeSuspend<Any?> { continuation -> edit.invoke(null, dataStore, transform, continuation) }
    }

    private suspend fun readSmokePreference(): Any? {
        val dataStore = dataStoreInstance()
        val flow = Class.forName("androidx.datastore.core.DataStore")
            .getMethod("getData")
            .invoke(dataStore) as Flow<*>
        val preferences = flow.first()
        return Class.forName("androidx.datastore.preferences.core.Preferences")
            .getMethod("get", Class.forName("androidx.datastore.preferences.core.Preferences\$Key"))
            .invoke(preferences, smokePreferenceKey())
    }

    private fun dataStoreInstance(): Any =
        Class.forName("com.metrolist.music.utils.DataStoreKt")
            .getMethod("getDataStore", AppContext::class.java)
            .invoke(null, AppContext.Default)

    private fun smokePreferenceKey(): Any =
        Class.forName("com.metrolist.music.constants.PreferenceKeysKt")
            .getMethod("getUseLoginForBrowse")
            .invoke(null)

    // 5. Queue persistence ----------------------------------------------------------------------

    private suspend fun checkQueuePersistence() {
        val started = System.currentTimeMillis()
        val budget = budget(60_000L)
        if (budget <= 0L) {
            fail("queue-persistence", started, "global time budget exhausted; code: SmokeTest.checkQueuePersistence")
            return
        }

        val outcome = withTimeoutOrNull(budget) { attempt { queueRoundTrip() } }
        when {
            outcome == null -> fail(
                "queue-persistence",
                started,
                "timed out after $budget ms; code: DesktopPlayerConnection.saveQueueToDisk()/restoreQueueFromDisk()",
            )
            outcome.isFailure -> {
                val error = outcome.exceptionOrNull()!!
                if (isHeadlessLimitation(error)) {
                    skip("queue-persistence", "cannot run headless: ${error.describe()}")
                } else {
                    fail(
                        "queue-persistence",
                        started,
                        "${error.describe()}; code: DesktopPlayerConnection.saveQueueToDisk()/restoreQueueFromDisk()",
                    )
                }
            }
            else -> pass("queue-persistence", started, outcome.getOrThrow())
        }
    }

    private suspend fun queueRoundTrip(): String {
        val db = database()

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val first = DesktopPlayerConnection(db, firstScope)
        try {
            first.queueTitle.value = "Smoke Queue"
            first.player.addMediaItems(
                listOf(
                    smokeMetadata("smokeq1", "Smoke One", "Tester", 120).toMediaItem(),
                    smokeMetadata("smokeq2", "Smoke Two", "Tester", 130).toMediaItem(),
                ),
            )
            first.currentMediaItemIndex.value = 1
        } finally {
            first.release()
            firstScope.cancel()
        }

        val queueFile = File(DesktopFiles.dataDir, "persistent_queue.data")
        if (!queueFile.isFile || queueFile.length() == 0L) {
            throw IllegalStateException(
                "persistent_queue.data was not written to ${queueFile.absolutePath}",
            )
        }

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val second = DesktopPlayerConnection(db, secondScope)
        try {
            val restored = withTimeoutOrNull(20_000L) {
                while (second.player.mediaItemCount < 2) delay(100L)
                true
            } ?: false
            if (!restored) {
                throw IllegalStateException(
                    "queue was not restored within 20 s (items=${second.player.mediaItemCount})",
                )
            }
            val items = (0 until second.player.mediaItemCount)
                .map { second.player.getMediaItemAt(it).mediaId }
            if (items != listOf("smokeq1", "smokeq2")) {
                throw IllegalStateException("restored media ids were $items")
            }
            if (second.currentMediaItemIndex.value != 1) {
                throw IllegalStateException("restored index was ${second.currentMediaItemIndex.value}, expected 1")
            }
            if (second.queueTitle.value != "Smoke Queue") {
                throw IllegalStateException("restored queue title was '${second.queueTitle.value}'")
            }
            return "saved ${queueFile.length()} bytes, restored items=$items, index=1, title=\"Smoke Queue\""
        } finally {
            second.release()
            secondScope.cancel()
        }
    }

    // 6. Backup ---------------------------------------------------------------------------------

    private suspend fun checkBackup() {
        val started = System.currentTimeMillis()
        val budget = budget(60_000L)
        if (budget <= 0L) {
            fail("backup", started, "global time budget exhausted; code: SmokeTest.checkBackup")
            return
        }

        val outcome = withTimeoutOrNull(budget) { attempt { createBackupZip() } }
        when {
            outcome == null -> fail(
                "backup",
                started,
                "timed out after $budget ms; code: BackupRestoreViewModel.backup()",
            )
            outcome.isFailure -> {
                val error = outcome.exceptionOrNull()!!
                if (isHeadlessLimitation(error)) {
                    skip("backup", "cannot run headless: ${error.describe()}")
                } else {
                    fail("backup", started, "${error.describe()}; code: BackupRestoreViewModel.backup()")
                }
            }
            else -> pass("backup", started, outcome.getOrThrow())
        }
    }

    private fun createBackupZip(): String {
        val viewModelClass = Class.forName("com.metrolist.music.viewmodels.BackupRestoreViewModel")
        val viewModel = viewModelClass
            .getConstructor(MusicDatabase::class.java)
            .newInstance(database())
        val target = File(DesktopFiles.dataDir, "smoke-backup.zip")
        target.delete()

        val backup = viewModelClass.getMethod("backup", AppContext::class.java, Any::class.java)
        backup.invoke(viewModel, AppContext.Default, target)

        if (!target.isFile || target.length() == 0L) {
            throw IllegalStateException(
                "backup zip was not created at ${target.absolutePath} (BackupRestoreViewModel logs the cause)",
            )
        }
        ZipFile(target).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            if ("settings.preferences_pb" !in entries) {
                throw IllegalStateException("settings.preferences_pb missing from backup entries=$entries")
            }
            if (MusicDatabase.DB_NAME !in entries) {
                throw IllegalStateException("${MusicDatabase.DB_NAME} missing from backup entries=$entries")
            }
            return "zip=${target.length()} bytes, entries=$entries"
        }
    }

    // Helpers -----------------------------------------------------------------------------------

    private fun database(): MusicDatabase =
        database ?: createDesktopDatabase().also { database = it }

    private fun smokeMetadata(id: String, title: String, artist: String, duration: Int): MediaMetadata =
        SongItem(
            id = id,
            title = title,
            artists = listOf(com.metrolist.innertube.models.Artist(name = artist, id = null)),
            duration = duration,
            thumbnail = "",
        ).toMediaMetadata()

    private fun remaining(): Long = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun budget(maxMs: Long): Long = minOf(maxMs, remaining())

    private fun pass(name: String, started: Long, vararg details: String) {
        passed++
        println("PASS $name (${System.currentTimeMillis() - started} ms)")
        details.forEach { println("  $it") }
    }

    private fun fail(name: String, started: Long, reason: String) {
        failed++
        println("FAIL $name: $reason (after ${System.currentTimeMillis() - started} ms)")
    }

    private fun skip(name: String, reason: String) {
        skipped++
        println("SKIP $name: $reason")
    }
}

private data class PlayableStream(
    val url: String,
    val headers: Map<String, String>,
    val client: String,
)

private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (t: Throwable) {
    Result.failure(t)
}

private suspend fun <T> invokeSuspend(call: (Continuation<T>) -> Any?): T =
    suspendCoroutineUninterceptedOrReturn { continuation -> call(continuation) }

private fun isHeadlessLimitation(t: Throwable): Boolean {
    val error = t.unwrapInvocation()
    val text = error.message.orEmpty()
    return error is java.awt.HeadlessException ||
        error is UnsatisfiedLinkError ||
        text.contains("headless", ignoreCase = true) ||
        text.contains("Toolkit", ignoreCase = true) ||
        text.contains("AWT", ignoreCase = true)
}

private fun Throwable.describe(): String {
    val error = unwrapInvocation()
    val message = error.message?.take(300).orEmpty()
    return if (message.isBlank()) error::class.simpleName ?: error.toString()
    else "${error::class.simpleName}: $message"
}

private fun Throwable.unwrapInvocation(): Throwable {
    var current = this
    while (current is InvocationTargetException && current.targetException != null) {
        current = current.targetException!!
    }
    return current
}
