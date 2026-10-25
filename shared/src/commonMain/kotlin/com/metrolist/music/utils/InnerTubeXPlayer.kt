package com.metrolist.music.utils
import com.metrolist.music.platform.AppContext

import com.metrolist.music.platform.KeyValueStore
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.Thumbnail
import com.metrolist.innertube.models.Thumbnails
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.extraction.strategy.ClientPoTokenCapabilities
import com.metrolist.innertubex.extraction.strategy.ClientSelectionMode
import com.metrolist.innertubex.extraction.strategy.ContentAwareFallbackStrategy
import com.metrolist.innertubex.extraction.strategy.PlaybackClientCatalog
import com.metrolist.innertubex.extraction.strategy.PlaybackClientCatalogView
import com.metrolist.innertubex.extraction.strategy.PlaybackClientManifest
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.platform.DesktopFiles
import com.metrolist.music.utils.potoken.PoTokenGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Clock
import java.io.File

/** The sole stream extraction entry point for the app. */
object InnerTubeXPlayer {
    private const val TAG = "InnerTubeXPlayer"
    private const val STREAM_CLIENT_FAILURE_TTL_MS = 5 * 60 * 1000L
    private const val DEFAULT_STREAM_TTL_SECONDS = 5 * 60

    @Volatile
    private var applicationContext: AppContext? = null

    @Volatile
    private var currentBundle: ExtractionBundle? = null

    private val bundleMutex = Mutex()
    private val streamClientFailures = java.util.concurrent.ConcurrentHashMap<String, FailedStreamClients>()

    @Synchronized
    fun initialize(context: AppContext) {
        if (applicationContext == null) applicationContext = context
    }

    suspend fun prewarm() = bundle().extractor.prewarm()

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: Any,
        contentHints: ContentHints = ContentHints(),
        allowBoundedRange: Boolean = true,
    ): Result<PlaybackData> =
        try {
            if (YouTube.visitorData.isNullOrBlank()) {
                YouTube.visitorData().getOrNull()?.takeIf { it.isNotBlank() }?.let {
                    YouTube.visitorData = it
                }
            }
            val hints =
                contentHints.copy(
                    isUploaded =
                        contentHints.isUploaded == true ||
                            playlistId == "MLPT" ||
                            playlistId?.contains("MLPT") == true,
                ).withStreamCapabilities(
                    allowHls = false,
                    allowSabr = false,
                    allowBoundedRange = allowBoundedRange,
                )
            val excludedClients = failedStreamClients(videoId)
            val stream =
                requireNotNull(
                    bundle().extractor.extract(
                        videoId = videoId,
                        hints = hints,
                        excludedClients = excludedClients,
                        audioQuality = audioQuality.toInnerTubeX(connectivityManager),
                        clientPlaybackNonce = generateClientPlaybackNonce(),
                    ),
                ) { "InnerTubeX returned no playable stream" }
            check(stream.sabrBootstrap == null) { "SABR is not supported by this playback engine" }
            Result.success(stream.toPlaybackData())
        } catch (error: CancellationException) {
            throw error
        } catch (error: StreamResolveException) {
            val cause = error.cause
            Result.failure(
                if (error.reason == StreamResolveException.Reason.NETWORK && cause != null) {
                    cause
                } else {
                    error
                },
            )
        } catch (error: Exception) {
            Result.failure(error)
        }

    internal fun markStreamClientFailed(
        videoId: String,
        clientName: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        streamClientFailures.compute(videoId) { _, failures ->
            FailedStreamClients(failures?.clientNames.orEmpty() + clientName, nowMs)
        }
    }

    fun clearStreamClientFailures() {
        streamClientFailures.clear()
    }

    suspend fun refreshAfterStreamRejection(): Boolean = bundle().cipherService.refreshAfterStreamRejection()

    internal fun failedStreamClients(
        videoId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Set<String> {
        val failures = streamClientFailures[videoId] ?: return emptySet()
        if ((nowMs - failures.failedAtMs) !in 0 until STREAM_CLIENT_FAILURE_TTL_MS) {
            streamClientFailures.remove(videoId, failures)
            return emptySet()
        }
        return failures.clientNames
    }

    private val promotedClientIds = setOf(
        "ANDROID_VR_NO_AUTH",
        "ANDROID_VR_1_65_10",
        "ANDROID_VR_1_61_48",
        "TVHTML5",
        "TVHTML5_DOWNGRADED",
        "TVHTML5_SIMPLY",
        "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
        "VISIONOS_0_1",
        "VISIONOS",
        "IOS",
        "IPADOS",
        "ANDROID",
        "WEB_REMIX",
    )

    private val desktopCatalogView = object : PlaybackClientCatalogView by PlaybackClientCatalog {
        private fun adaptManifest(manifest: PlaybackClientManifest): PlaybackClientManifest {
            val shouldPromote = manifest.id in promotedClientIds
            val noPoTokenAvailable = tokenProvider.capabilities.providers.isEmpty()
            return manifest.copy(
                selectionMode = if (shouldPromote) ClientSelectionMode.AUTOMATIC else manifest.selectionMode,
                poTokens = if (noPoTokenAvailable) ClientPoTokenCapabilities() else manifest.poTokens,
            )
        }

        override fun find(id: String): com.metrolist.innertubex.extraction.strategy.PlaybackClientOption? {
            val option = PlaybackClientCatalog.find(id) ?: return null
            return option.copy(manifest = adaptManifest(option.manifest))
        }

        override val automaticManifests: List<PlaybackClientManifest>
            get() {
                val baseIds = LinkedHashSet<String>()
                PlaybackClientCatalog.automaticManifests.forEach { baseIds.add(it.id) }
                promotedClientIds.forEach { baseIds.add(it) }
                return baseIds.mapNotNull { id -> PlaybackClientCatalog.findManifest(id)?.let(::adaptManifest) }
                    .filter { it.selectionMode == ClientSelectionMode.AUTOMATIC }
            }
    }

    suspend fun deobfuscateCipher(videoId: String, cipher: String): String? {
        return runCatching {
            val direct = bundle().cipherService.deobfuscateSignatureCipher(videoId, cipher) ?: return@runCatching null
            bundle().cipherService.processNParameter(videoId, direct)
        }.getOrNull()
    }

    private suspend fun bundle(): ExtractionBundle {
        val transport = YouTube.extractionTransport()
        currentBundle?.takeIf { it.transportGeneration == transport.generation }?.let { return it }
        return bundleMutex.withLock {
            val latestTransport = YouTube.extractionTransport()
            currentBundle?.takeIf { it.transportGeneration == latestTransport.generation }?.let { return@withLock it }
            try {
                currentBundle?.cipherService?.dispose()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(
                    com.metrolist.innertubex.InnerTubeLogEvent(
                        level = InnerTubeLogLevel.WARN,
                        tag = TAG,
                        message = "old cipher disposal failed",
                        details = mapOf("exceptionType" to (error::class.simpleName ?: "unknown")),
                    ),
                )
            }

            val remoteStore = RemotePlayerConfigStore(latestTransport.httpClient, configRepository, logger)
            val cipherService = YouTubeCipherService(latestTransport.httpClient, remoteStore, logger)
            // DESKTOP: innertubex only keeps the player JS in memory, so every cold start evaluates
            // the ~2.6 MB script again (3-4 s CPU, see B7). The library exposes a cache hook for the
            // EJS-preprocessed player; back it with a small on-disk cache under
            // DesktopFiles.cacheDir/player-js so a warm start skips that evaluation.
            cipherService.setPreprocessedPlayerCache(
                read = { key -> PlayerJsDiskCache.read(key) },
                write = { key, script -> PlayerJsDiskCache.write(key, script) },
            )
            val extractor =
                InnerTubeExtractor(
                    configParser =
                        YtConfigParserImpl(
                            latestTransport.httpClient,
                            latestTransport.innerTube,
                            remoteStore,
                            logger,
                        ).withEmbeddedConfigFallback(),
                    cipherService = cipherService,
                    innerTube = latestTransport.innerTube,
                    tokenProvider = tokenProvider,
                    fallbackStrategy = ContentAwareFallbackStrategy(catalog = desktopCatalogView),
                    logger = logger,
                )
            ExtractionBundle(latestTransport.generation, cipherService, extractor).also { currentBundle = it }
        }
    }

    private val configRepository: PlayerConfigRepository by lazy {
        DesktopPlayerConfigRepository()
    }

    private val poTokenGenerator: PoTokenGenerator by lazy {
        PoTokenGenerator(requireNotNull(applicationContext) { "InnerTubeXPlayer is not initialized" })
    }

    private val tokenProvider =
        object : TokenProvider {
            override val capabilities =
                TokenProviderCapabilities(
                    providers = emptySet(),
                    usesWebView = false,
                )

            override suspend fun getPoToken(
                videoId: String,
                visitorData: String,
                cookie: String?,
            ): PoTokenResult? =
                null

            override suspend fun close() {
                poTokenGenerator.close()
            }
        }

    private val logger =
        InnerTubeLogger { event ->
            val details = event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
            val message = event.message + details.takeUnless { event.details.isEmpty() }.orEmpty()
            when (event.level) {
                InnerTubeLogLevel.DEBUG -> Timber.tag(event.tag).d(message)
                InnerTubeLogLevel.INFO -> Timber.tag(event.tag).i(message)
                InnerTubeLogLevel.WARN -> Timber.tag(event.tag).w(message)
                InnerTubeLogLevel.ERROR -> Timber.tag(event.tag).e(message)
            }
        }

    class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val streamClient: String,
        val streamHeaders: Map<String, String>,
        val requireBoundedRange: Boolean,
        val rangeChunkSizeBytes: Long,
        val useRangeChunks: Boolean,
    )

    private data class ExtractionBundle(
        val transportGeneration: Long,
        val cipherService: YouTubeCipherService,
        val extractor: InnerTubeExtractor,
    )

    private data class FailedStreamClients(
        val clientNames: Set<String>,
        val failedAtMs: Long,
    )

    private class DesktopPlayerConfigRepository : PlayerConfigRepository {
        private val preferences = KeyValueStore.get("innertubex_player_config")

        override val enabled: Boolean = true
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL
        override var cachedJson: String
            get() = preferences.getString("json", "").orEmpty()
            set(value) { preferences.edit().putString("json", value).commit() }
        override var cachedAtMs: Long
            get() = preferences.getLong("cached_at_ms", 0L)
            set(value) { preferences.edit().putLong("cached_at_ms", value).commit() }
        override var cachedSourceUrl: String
            get() = preferences.getString("source_url", "").orEmpty()
            set(value) { preferences.edit().putString("source_url", value).commit() }
        override var cachedEtag: String
            get() = preferences.getString("etag", "").orEmpty()
            set(value) { preferences.edit().putString("etag", value).commit() }

        private companion object {
            const val PLAYER_CONFIG_URL =
                "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
        }
    }

    internal fun YtConfigParser.withEmbeddedConfigFallback(): YtConfigParser =
        object : YtConfigParser by this {
            override suspend fun fetchConfig(
                videoId: String,
                useLoginCookies: Boolean,
            ) =
                try {
                    this@withEmbeddedConfigFallback.fetchConfig(videoId, useLoginCookies)
                } catch (_: IllegalStateException) {
                    this@withEmbeddedConfigFallback.fetchEmbeddedConfig(videoId, useLoginCookies = false)
                }
        }

    private fun AudioQuality.toInnerTubeX(connectivityManager: Any): InnerTubeXAudioQuality =
        when (this) {
            AudioQuality.HIGH -> InnerTubeXAudioQuality.HIGH
            AudioQuality.LOW -> InnerTubeXAudioQuality.LOW
            AudioQuality.AUTO ->
                if (false) {
                    InnerTubeXAudioQuality.LOW
                } else {
                    InnerTubeXAudioQuality.AUTO
                }
        }

    private fun ExtractedStream.toPlaybackData(): PlaybackData {
        val metadata = mediaMetadata
        val tracking = playbackTracking
        val fullMimeType =
            if (codecs.isNullOrBlank()) {
                mimeType.orEmpty()
            } else {
                "${mimeType.orEmpty()}; codecs=\"$codecs\""
            }
        return PlaybackData(
            audioConfig =
                if (loudnessDb != null || perceptualLoudnessDb != null) {
                    PlayerResponse.PlayerConfig.AudioConfig(loudnessDb, perceptualLoudnessDb)
                } else {
                    null
                },
            videoDetails =
                metadata?.let {
                    PlayerResponse.VideoDetails(
                        videoId = videoId,
                        title = it.title,
                        author = it.author,
                        channelId = it.channelId.orEmpty(),
                        lengthSeconds = it.durationSeconds?.toString().orEmpty(),
                        musicVideoType = it.musicVideoType,
                        viewCount = it.viewCount,
                        thumbnail =
                            Thumbnails(
                                it.thumbnails.map { thumbnail ->
                                    Thumbnail(thumbnail.url, thumbnail.width, thumbnail.height)
                                },
                            ),
                    )
                },
            playbackTracking =
                tracking?.let {
                    PlayerResponse.PlaybackTracking(
                        videostatsPlaybackUrl =
                            it.playbackUrl?.let(PlayerResponse.PlaybackTracking::VideostatsPlaybackUrl),
                        videostatsWatchtimeUrl =
                            it.watchtimeUrl?.let(PlayerResponse.PlaybackTracking::VideostatsWatchtimeUrl),
                        atrUrl = null,
                    )
                },
            format =
                PlayerResponse.StreamingData.Format(
                    itag = itag,
                    url = audioUrl,
                    mimeType = fullMimeType,
                    bitrate = bitrate ?: 0,
                    width = null,
                    height = null,
                    contentLength = contentLengthBytes,
                    quality = "",
                    fps = null,
                    qualityLabel = null,
                    averageBitrate = bitrate,
                    audioQuality = null,
                    approxDurationMs = metadata?.durationSeconds?.times(1000)?.toString(),
                    audioSampleRate = sampleRate,
                    audioChannels = null,
                    loudnessDb = loudnessDb,
                    lastModified = null,
                    signatureCipher = null,
                    cipher = null,
                    audioTrack = null,
                ),
            streamUrl = audioUrl,
            streamExpiresInSeconds =
                expiresAt
                    ?.let { ((it.toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()) / 1000L).toInt() }
                    ?.coerceAtLeast(1)
                    ?: DEFAULT_STREAM_TTL_SECONDS,
            streamClient = clientName,
            streamHeaders = headers,
            requireBoundedRange = this.requireBoundedRange,
            rangeChunkSizeBytes = this.rangeChunkSizeBytes,
            useRangeChunks = this.useRangeChunks,
        )
    }
}

/**
 * DESKTOP: disk cache for the EJS-preprocessed player script (B7).
 *
 * innertubex keeps only the raw script in memory, so a cold start has to run the 2.6 MB player
 * through the JS engine again. The library keys its preprocessed cache by
 * `sha1("$playerUrl:$bundleHash")` and passes that key to the read/write hooks, so the file name is
 * the key; a new player id changes the URL and therefore the key. At most [MAX_ENTRIES] versions are
 * kept on disk, the oldest is deleted after every write.
 */
private object PlayerJsDiskCache {
    private const val TAG = "PlayerJsCache"
    private const val DIR_NAME = "player-js"
    private const val MAX_ENTRIES = 3
    private const val MIN_VALID_LENGTH = 32
    private val NON_FILE_CHAR = Regex("[^A-Za-z0-9._-]")

    private val mutex = Mutex()

    private val directory: File
        get() = File(DesktopFiles.cacheDir, DIR_NAME)

    suspend fun read(key: String): String? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = fileFor(key)
            if (!file.isFile) {
                Timber.tag(TAG).d("player JS cache miss key=%s", key)
                return@withContext null
            }
            val startedAt = System.currentTimeMillis()
            val script = runCatching { file.readText() }.getOrNull()
            if (script == null || script.length < MIN_VALID_LENGTH) {
                // A truncated or unreadable entry would make the JS engine fail, so drop it.
                runCatching { file.delete() }
                Timber.tag(TAG).w("player JS cache entry dropped key=%s", key)
                return@withContext null
            }
            // Touch the file so the 3-entry LRU keeps the most recently used version.
            runCatching { file.setLastModified(System.currentTimeMillis()) }
            Timber.tag(TAG).d(
                "player JS cache hit key=%s size=%d elapsed=%dms",
                key,
                script.length,
                System.currentTimeMillis() - startedAt,
            )
            script
        }
    }

    suspend fun write(key: String, script: String?) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = fileFor(key)
            if (script.isNullOrBlank()) {
                // The library evicts an unusable entry by writing null.
                runCatching { file.delete() }
                return@withContext
            }
            val startedAt = System.currentTimeMillis()
            runCatching {
                directory.mkdirs()
                val temp = File(directory, "${file.name}.tmp")
                temp.writeText(script)
                if (!temp.renameTo(file)) {
                    file.writeText(script)
                    temp.delete()
                }
            }.onFailure { Timber.tag(TAG).w(it, "player JS cache write failed key=%s", key) }
            trim()
            Timber.tag(TAG).d(
                "player JS cache stored key=%s size=%d elapsed=%dms entries=%d",
                key,
                script.length,
                System.currentTimeMillis() - startedAt,
                entries().size,
            )
        }
    }

    private fun trim() {
        entries().drop(MAX_ENTRIES).forEach { file ->
            runCatching { file.delete() }
            Timber.tag(TAG).d("player JS cache evicted key=%s", file.nameWithoutExtension)
        }
    }

    private fun entries(): List<File> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(".js") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    private fun fileFor(key: String): File =
        File(directory, key.replace(NON_FILE_CHAR, "_").take(80) + ".js")
}




