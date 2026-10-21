package com.metrolist.music.playback

import androidx.media3.common.Timeline
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Event
import com.metrolist.music.db.entities.FormatEntity
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.defaultDesktopContext
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.nowLocalDateTime
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.Queue
import com.metrolist.music.utils.InnerTubeXPlayer
import com.sun.jna.NativeLibrary
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPlayerConnection(
    private val database: MusicDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) : PlayerConnection {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolvedStreams = ConcurrentHashMap<String, ResolvedStreamInfo>()
    private val streamProxy = DesktopStreamProxy()

    private var mediaPlayerFactory: MediaPlayerFactory? = null
    private var vlcPlayer: MediaPlayer? = null

    private var activeQueue: Queue? = null
    private var playJob: Job? = null

    override val playbackState = MutableStateFlow(Player.STATE_IDLE)
    override val isPlaying = MutableStateFlow(false)
    override val isEffectivelyPlaying: StateFlow<Boolean> get() = isPlaying
    override val mediaMetadata = MutableStateFlow<MediaMetadata?>(null)

    override val currentSong: StateFlow<Song?> =
        mediaMetadata.flatMapLatest { meta ->
            if (meta?.id != null) database.song(meta.id) else flowOf(null)
        }.stateIn(scope, SharingStarted.Lazily, null)

    override val currentLyrics: StateFlow<LyricsEntity?> =
        mediaMetadata.flatMapLatest { meta ->
            if (meta?.id != null) database.lyrics(meta.id) else flowOf(null)
        }.stateIn(scope, SharingStarted.Lazily, null)

    override val currentFormat: StateFlow<FormatEntity?> =
        mediaMetadata.flatMapLatest { meta ->
            if (meta?.id != null) database.format(meta.id) else flowOf(null)
        }.stateIn(scope, SharingStarted.Lazily, null)

    override val queueTitle = MutableStateFlow<String?>(null)
    override val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    override val currentMediaItemIndex = MutableStateFlow(0)
    override val currentWindowIndex = MutableStateFlow(0)
    override val shuffleModeEnabled = MutableStateFlow(false)
    override val repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    override val canSkipPrevious = MutableStateFlow(false)
    override val canSkipNext = MutableStateFlow(false)
    override val isMuted = MutableStateFlow(false)
    override val error = MutableStateFlow<PlaybackException?>(null)
    override val currentStreamClient = MutableStateFlow<String?>(null)
    override val waitingForNetworkConnection = MutableStateFlow(false)
    override var allowInternalSync: Boolean = true

    override val service: MusicPlayerService = object : MusicPlayerService {
        override val playerVolume = MutableStateFlow(1f)
        override val sleepTimer = SleepTimer(onExpired = { pause() })
        override val isPlayerReady = MutableStateFlow(true)
    }

    override val player: DesktopPlayer = DesktopPlayer()

    init {
        InnerTubeXPlayer.initialize(defaultDesktopContext)
        initVlcEngine()

        // Volume synchronization
        scope.launch {
            service.playerVolume.collect { vol ->
                val vlcVol = (vol.coerceIn(0f, 1f) * 100f).roundToInt()
                runCatching { vlcPlayer?.audio()?.setVolume(if (isMuted.value) 0 else vlcVol) }
            }
        }

        // Position & duration polling loop while active
        scope.launch {
            while (isActive) {
                val mp = vlcPlayer
                if (mp != null && isPlaying.value) {
                    val time = runCatching { mp.status().time() }.getOrDefault(-1L)
                    if (time >= 0L) {
                        player.internalPosition = time
                    }
                    val length = runCatching { mp.status().length() }.getOrDefault(-1L)
                    if (length > 0L) {
                        player.internalDuration = length
                    }
                }
                delay(200)
            }
        }
    }

    private fun initVlcEngine() {
        try {
            val localAppData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            val vlcBase = File(localAppData, "Metrolist/vlc")
            val candidateDirs = buildList {
                add(File(vlcBase, "vlc-3.0.24"))
                if (vlcBase.exists()) {
                    vlcBase.listFiles()?.filter { it.isDirectory }?.forEach { add(it) }
                }
                add(File("C:\\Program Files\\VideoLAN\\VLC"))
            }
            val vlcDir = candidateDirs.firstOrNull { File(it, "libvlc.dll").exists() }
            if (vlcDir != null) {
                val path = vlcDir.absolutePath
                val pluginsPath = File(vlcDir, "plugins").absolutePath
                System.setProperty("jna.library.path", path)
                System.setProperty("VLC_PLUGIN_PATH", pluginsPath)
                NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), path)
                NativeLibrary.addSearchPath("libvlccore", path)
                Timber.d("Loaded 64-bit libvlc from: $path")
            }

            val factory = MediaPlayerFactory(
                "--no-video",
                "--no-stats",
                "--network-caching=1500",
                "--http-reconnect",
            )
            mediaPlayerFactory = factory
            val mp = factory.mediaPlayers().newMediaPlayer()
            mp.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                    if (newCache < 100f && !isPlaying.value) {
                        playbackState.value = Player.STATE_BUFFERING
                        player.notifyPlaybackState(Player.STATE_BUFFERING)
                    }
                }

                override fun playing(mediaPlayer: MediaPlayer) {
                    playbackState.value = Player.STATE_READY
                    isPlaying.value = true
                    player.playWhenReady = true
                    player.notifyPlaybackState(Player.STATE_READY)
                    player.notifyIsPlaying(true)
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    isPlaying.value = false
                    player.playWhenReady = false
                    player.notifyIsPlaying(false)
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    isPlaying.value = false
                    player.notifyIsPlaying(false)
                }

                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    if (newTime >= 0L) {
                        player.internalPosition = newTime
                    }
                }

                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                    if (newLength > 0L) {
                        player.internalDuration = newLength
                    }
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    ioScope.launch {
                        onTrackEndedNaturally()
                    }
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    Timber.e("VLCJ media player error for ${mediaMetadata.value?.id}")
                    isPlaying.value = false
                    playbackState.value = Player.STATE_IDLE
                    error.value = PlaybackException(
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        "Playback error",
                    )
                }
            })
            vlcPlayer = mp
            com.metrolist.music.ui.component.DownloadCenter.engine = createDownloadEngine()
            runCatching { com.metrolist.music.eq.EqualizerService.attach(VlcEqualizerBackend(factory, mp)) }
                .onFailure { Timber.e(it, "EQ: VLC equalizer unavailable") }
        } catch (e: Throwable) {
            Timber.e(e, "Failed to initialize VLCJ audio engine")
        }
    }

    private fun onTrackEndedNaturally() {
        val currentMeta = mediaMetadata.value
        val dur = player.duration.takeIf { it > 0 } ?: ((currentMeta?.duration ?: 0) * 1000L)
        if (currentMeta != null && dur > 0) {
            database.query {
                incrementTotalPlayTime(currentMeta.id, dur)
                insert(
                    Event(
                        songId = currentMeta.id,
                        timestamp = nowLocalDateTime(),
                        playTime = dur,
                    )
                )
            }
        }

        if (service.sleepTimer.isActive && service.sleepTimer.pauseWhenSongEnd) {
            service.sleepTimer.clear()
            pause()
            return
        }

        when (repeatMode.value) {
            Player.REPEAT_MODE_ONE -> {
                playIndex(currentMediaItemIndex.value, 0L)
            }
            Player.REPEAT_MODE_ALL -> {
                val count = player.mediaItemCount
                if (count > 0) {
                    val next = (currentMediaItemIndex.value + 1) % count
                    playIndex(next, 0L)
                }
            }
            else -> {
                val next = currentMediaItemIndex.value + 1
                if (next < player.mediaItemCount) {
                    playIndex(next, 0L)
                } else {
                    playbackState.value = Player.STATE_ENDED
                    isPlaying.value = false
                    player.notifyPlaybackState(Player.STATE_ENDED)
                    player.notifyIsPlaying(false)
                }
            }
        }
    }

    override fun playQueue(queue: Queue) {
        activeQueue = queue
        playJob?.cancel()
        playJob = ioScope.launch {
            try {
                error.value = null
                playbackState.value = Player.STATE_BUFFERING
                queue.preloadItem?.let { preload ->
                    mediaMetadata.value = preload
                    player.internalDuration = (preload.duration.takeIf { it > 0 } ?: 0) * 1000L
                }

                val status = queue.getInitialStatus()
                queueTitle.value = status.title
                val items = status.items
                if (items.isEmpty()) {
                    playbackState.value = Player.STATE_IDLE
                    return@launch
                }

                val startIndex = status.mediaItemIndex.coerceIn(0, items.lastIndex)
                player.setItemsInternal(items, startIndex)
                playIndex(startIndex, status.position)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load queue")
                playbackState.value = Player.STATE_IDLE
                error.value = PlaybackException(
                    PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    e.message ?: "Failed to load queue",
                    e,
                )
            }
        }
    }

    override fun playQueue(queue: Any, index: Int) {
        if (queue is Queue) {
            playQueue(queue)
        }
    }

    private fun playIndex(index: Int, startPositionMs: Long = 0L) {
        val items = player.itemsList
        if (index !in items.indices) return

        player.updateCurrentIndexInternal(index)
        val item = items[index]
        val meta = item.metadata
        mediaMetadata.value = meta
        error.value = null
        player.internalPosition = startPositionMs
        player.internalDuration = ((meta?.duration?.takeIf { it > 0 } ?: 0) * 1000L)
        playbackState.value = Player.STATE_BUFFERING
        player.notifyMediaItemTransition(item, 2) // MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED / SEEK

        if (meta != null) {
            ioScope.launch {
                runCatching {
                    database.query {
                        insert(meta)
                    }
                }
            }
        }

        // Prefetch next page if near the end of the queue
        val q = activeQueue
        if (q != null && index >= items.size - 3 && q.hasNextPage()) {
            ioScope.launch {
                runCatching {
                    val more = q.nextPage()
                    if (more.isNotEmpty()) {
                        player.addMediaItems(more)
                    }
                }
            }
        }

        playJob?.cancel()
        playJob = ioScope.launch {
            val videoId = item.mediaId.ifBlank { meta?.id.orEmpty() }
            if (videoId.isBlank()) return@launch

            val localFile = com.metrolist.music.ui.component.DownloadCenter.localFile(videoId)
            if (localFile != null) {
                val mp = vlcPlayer
                if (mp != null) {
                    Timber.i("Playing downloaded file for %s", videoId)
                    val vol = (service.playerVolume.value.coerceIn(0f, 1f) * 100f).roundToInt()
                    mp.audio().setVolume(if (isMuted.value) 0 else vol)
                    val options = if (startPositionMs > 0L) arrayOf(":start-time=${startPositionMs / 1000.0}") else emptyArray()
                    mp.media().play(localFile.absolutePath, *options)
                    return@launch
                }
            }

            val streamResult = resolveStream(videoId, meta)
            streamResult.onSuccess { resolved ->
                resolvedStreams[videoId] = resolved
                currentStreamClient.value = resolved.clientName
                if (resolved.durationMs > 0 && player.internalDuration <= 0L) {
                    player.internalDuration = resolved.durationMs
                }

                val proxyUrl = streamProxy.getStreamUrl(videoId)
                val mp = vlcPlayer
                if (mp != null) {
                    val vol = (service.playerVolume.value.coerceIn(0f, 1f) * 100f).roundToInt()
                    mp.audio().setVolume(if (isMuted.value) 0 else vol)
                    val options = if (startPositionMs > 0L) {
                        arrayOf(":start-time=${startPositionMs / 1000.0}")
                    } else {
                        emptyArray()
                    }
                    mp.media().play(proxyUrl, *options)
                }
            }.onFailure { err ->
                Timber.e(err, "Failed to resolve stream for $videoId")
                playbackState.value = Player.STATE_IDLE
                isPlaying.value = false
                error.value = PlaybackException(
                    PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    err.message ?: "Failed to resolve stream",
                    err,
                )
            }
        }
    }

    private suspend fun resolveStream(
        videoId: String,
        meta: MediaMetadata?,
    ): Result<ResolvedStreamInfo> {
        resolvedStreams[videoId]?.takeIf { !it.isExpired() }?.let {
            return Result.success(it)
        }

        if (YouTube.visitorData.isNullOrBlank()) {
            YouTube.visitorData().getOrNull()?.takeIf { it.isNotBlank() }?.let {
                YouTube.visitorData = it
            }
        }

        // 1. Try InnerTubeXPlayer
        val innerTubeXResult = InnerTubeXPlayer.playerResponseForPlayback(
            videoId = videoId,
            audioQuality = AudioQuality.AUTO,
            connectivityManager = Any(),
            contentHints = ContentHints(
                isExplicit = meta?.explicit,
                isUploaded = false,
            ),
        )

        innerTubeXResult.getOrNull()?.let { data ->
            val format = data.format
            val contentLength = format.contentLength ?: 0L
            if (contentLength > 0L) {
                runCatching {
                    database.query {
                        upsert(
                            FormatEntity(
                                id = videoId,
                                itag = format.itag,
                                mimeType = format.mimeType.substringBefore(";"),
                                codecs = format.mimeType
                                    .substringAfter("codecs=", missingDelimiterValue = "")
                                    .substringBefore(";")
                                    .trim()
                                    .removeSurrounding("\""),
                                bitrate = format.bitrate,
                                sampleRate = format.audioSampleRate,
                                contentLength = contentLength,
                                loudnessDb = data.audioConfig?.loudnessDb,
                                perceptualLoudnessDb = data.audioConfig?.perceptualLoudnessDb,
                                playbackUrl = data.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
                            )
                        )
                    }
                }
            }
            val durationMs = format.approxDurationMs?.toLongOrNull()
                ?: data.videoDetails?.lengthSeconds?.toLongOrNull()?.times(1000L)
                ?: 0L
            return Result.success(
                ResolvedStreamInfo(
                    videoId = videoId,
                    streamUrl = data.streamUrl,
                    headers = data.streamHeaders,
                    mimeType = format.mimeType.substringBefore(";").ifBlank { "audio/webm" },
                    contentLength = contentLength.takeIf { it > 0L },
                    durationMs = durationMs,
                    clientName = data.streamClient,
                    expiresAtMs = System.currentTimeMillis() + (data.streamExpiresInSeconds * 1000L),
                )
            )
        }

        InnerTubeXPlayer.clearStreamClientFailures()
        // 2. Fallback to direct YouTube.player across verified direct-URL & decipherable clients
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
                com.metrolist.innertubex.extraction.strategy.PlaybackClientCatalog.findManifest(id)?.client?.let(::add)
            }
            add(YouTubeClient.TVHTML5)
            add(YouTubeClient.TVHTML5_DOWNGRADED)
            add(YouTubeClient.ANDROID_VR_NO_AUTH)
            add(YouTubeClient.ANDROID_VR_1_65_10)
            add(YouTubeClient.IOS)
            add(YouTubeClient.WEB_REMIX)
            add(YouTubeClient.WEB)
        }.distinctBy { "${it.clientName}:${it.clientVersion}" }

        for (client in fallbackClients) {
            val response = YouTube.player(videoId, client = client).getOrNull() ?: continue
            val allAudioFormats = (response.streamingData?.adaptiveFormats.orEmpty() + response.streamingData?.formats.orEmpty())
                .filter { it.mimeType.startsWith("audio/") }
            if (allAudioFormats.isEmpty()) continue

            val directFormat = allAudioFormats.filter { !it.url.isNullOrBlank() }.maxByOrNull { it.bitrate }
            val cipherFormat = allAudioFormats.firstOrNull { !it.signatureCipher.isNullOrBlank() || !it.cipher.isNullOrBlank() }
            val bestFormat = directFormat ?: cipherFormat ?: continue

            var url = bestFormat.url
            if (url.isNullOrBlank()) {
                val cipher = bestFormat.signatureCipher ?: bestFormat.cipher
                if (!cipher.isNullOrBlank()) {
                    url = InnerTubeXPlayer.deobfuscateCipher(videoId, cipher)
                }
            }
            if (url.isNullOrBlank()) continue
            val contentLength = bestFormat.contentLength ?: 0L
            if (contentLength > 0L) {
                runCatching {
                    database.query {
                        upsert(
                            FormatEntity(
                                id = videoId,
                                itag = bestFormat.itag,
                                mimeType = bestFormat.mimeType.substringBefore(";"),
                                codecs = bestFormat.mimeType
                                    .substringAfter("codecs=", missingDelimiterValue = "")
                                    .substringBefore(";")
                                    .trim()
                                    .removeSurrounding("\""),
                                bitrate = bestFormat.bitrate,
                                sampleRate = bestFormat.audioSampleRate,
                                contentLength = contentLength,
                                loudnessDb = response.playerConfig?.audioConfig?.loudnessDb,
                                perceptualLoudnessDb = response.playerConfig?.audioConfig?.perceptualLoudnessDb,
                                playbackUrl = response.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
                            )
                        )
                    }
                }
            }
            val durationMs = bestFormat.approxDurationMs?.toLongOrNull()
                ?: response.videoDetails?.lengthSeconds?.toLongOrNull()?.times(1000L)
                ?: 0L
            return Result.success(
                ResolvedStreamInfo(
                    videoId = videoId,
                    streamUrl = url,
                    headers = mapOf("User-Agent" to client.userAgent),
                    mimeType = bestFormat.mimeType.substringBefore(";").ifBlank { "audio/webm" },
                    contentLength = contentLength.takeIf { it > 0L },
                    durationMs = durationMs,
                    clientName = client.clientName,
                    expiresAtMs = System.currentTimeMillis() + ((response.streamingData?.expiresInSeconds ?: 3600) * 1000L),
                )
            )
        }

        return Result.failure(
            innerTubeXResult.exceptionOrNull()
                ?: IllegalStateException("Could not resolve playable audio stream for $videoId")
        )
    }

    override fun togglePlayPause() {
        if (isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    override fun play() {
        val mp = vlcPlayer ?: return
        if (playbackState.value == Player.STATE_IDLE || playbackState.value == Player.STATE_ENDED) {
            if (player.mediaItemCount > 0) {
                playIndex(currentMediaItemIndex.value, player.currentPosition)
            }
        } else {
            mp.controls().play()
        }
    }

    override fun pause() {
        vlcPlayer?.controls()?.pause()
    }

    override fun seekTo(position: Long) {
        val target = position.coerceAtLeast(0L)
        player.internalPosition = target
        vlcPlayer?.controls()?.setTime(target)
    }

    override fun seekToNext() {
        val count = player.mediaItemCount
        if (count == 0) return
        val next = if (currentMediaItemIndex.value + 1 < count) {
            currentMediaItemIndex.value + 1
        } else if (repeatMode.value == Player.REPEAT_MODE_ALL) {
            0
        } else {
            return
        }
        playIndex(next, 0L)
    }

    override fun seekToPrevious() {
        if (player.currentPosition > 3000L) {
            seekTo(0L)
            return
        }
        val prev = if (currentMediaItemIndex.value > 0) {
            currentMediaItemIndex.value - 1
        } else if (repeatMode.value == Player.REPEAT_MODE_ALL && player.mediaItemCount > 0) {
            player.mediaItemCount - 1
        } else {
            0
        }
        playIndex(prev, 0L)
    }

    override fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    override fun addToQueue(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val wasEmpty = player.mediaItemCount == 0
        player.addMediaItems(items)
        if (wasEmpty) {
            playIndex(0, 0L)
        }
    }

    override fun addToQueue(song: SongItem) = addToQueue(song.toMediaItem())

    override fun playNext(item: MediaItem) = playNext(listOf(item))

    override fun playNext(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val wasEmpty = player.mediaItemCount == 0
        val insertIdx = if (wasEmpty) 0 else (currentMediaItemIndex.value + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItems(insertIdx, items)
        if (wasEmpty) {
            playIndex(0, 0L)
        }
    }

    override fun playNext(song: SongItem) = playNext(song.toMediaItem())

    override fun toggleLike() {
        val song = currentSong.value ?: return
        ioScope.launch {
            database.query {
                update(song.song.toggleLike())
            }
        }
    }

    override fun toggleLibrary() {
        val song = currentSong.value ?: return
        ioScope.launch {
            database.query {
                update(song.song.toggleLibrary())
            }
        }
    }

    override fun toggleMute() {
        setMuted(!isMuted.value)
    }

    override fun setMuted(muted: Boolean) {
        isMuted.value = muted
        val vol = (service.playerVolume.value.coerceIn(0f, 1f) * 100f).roundToInt()
        runCatching { vlcPlayer?.audio()?.setVolume(if (muted) 0 else vol) }
    }

    override fun toggleRepeatMode() {
        player.toggleRepeatMode()
    }

    override fun startRadioSeamlessly() {}

    override fun refreshSongMetadata(song: Song) {
        if (mediaMetadata.value?.id == song.id) {
            mediaMetadata.value = song.toMediaMetadata()
        }
    }

    private fun createDownloadEngine() = object : com.metrolist.music.ui.component.DownloadCenter.Engine {
        override suspend fun resolve(meta: MediaMetadata): com.metrolist.music.ui.component.DownloadCenter.StreamSource {
            runCatching { database.query { insert(meta) } }
            val info = resolveStream(meta.id, meta).getOrThrow()
            return com.metrolist.music.ui.component.DownloadCenter.StreamSource(
                url = info.streamUrl,
                headers = info.headers,
                mimeType = info.mimeType,
                contentLength = info.contentLength,
            )
        }

        override suspend fun markDownloaded(songId: String, downloaded: Boolean) {
            database.updateDownloadedInfo(songId, downloaded, if (downloaded) nowLocalDateTime() else null)
        }
    }

    fun release() {
        playJob?.cancel()
        runCatching { vlcPlayer?.controls()?.stop() }
        runCatching { vlcPlayer?.release() }
        runCatching { mediaPlayerFactory?.release() }
        runCatching { streamProxy.stop() }
    }

    inner class DesktopPlayer : Player() {
        internal val itemsList = CopyOnWriteArrayList<MediaItem>()
        private val listeners = CopyOnWriteArrayList<Listener>()

        @Volatile
        internal var internalPosition: Long = 0L

        @Volatile
        internal var internalDuration: Long = 0L

        override val playbackState: Int
            get() = this@DesktopPlayerConnection.playbackState.value

        override var playWhenReady: Boolean = false
            set(value) {
                field = value
                if (value && !isPlaying) {
                    this@DesktopPlayerConnection.play()
                } else if (!value && isPlaying) {
                    this@DesktopPlayerConnection.pause()
                }
            }

        override val currentPosition: Long
            get() = internalPosition

        override val duration: Long
            get() = internalDuration

        override val repeatMode: Int
            get() = this@DesktopPlayerConnection.repeatMode.value

        override var shuffleModeEnabled: Boolean
            get() = this@DesktopPlayerConnection.shuffleModeEnabled.value
            set(value) {
                this@DesktopPlayerConnection.shuffleModeEnabled.value = value
            }

        override val currentMediaItem: MediaItem?
            get() = itemsList.getOrNull(currentMediaItemIndex)

        override val currentTimeline: Timeline
            get() = Timeline(queueWindows.value)

        override val currentMediaItemIndex: Int
            get() = this@DesktopPlayerConnection.currentMediaItemIndex.value

        override val mediaItemCount: Int
            get() = itemsList.size

        override val isPlaying: Boolean
            get() = this@DesktopPlayerConnection.isPlaying.value

        override val isCurrentMediaItemSeekable: Boolean
            get() = true

        override val previousMediaItemIndex: Int
            get() = if (currentMediaItemIndex > 0) currentMediaItemIndex - 1 else if (repeatMode == REPEAT_MODE_ALL && itemsList.isNotEmpty()) itemsList.lastIndex else -1

        override val nextMediaItemIndex: Int
            get() = if (currentMediaItemIndex + 1 < itemsList.size) currentMediaItemIndex + 1 else if (repeatMode == REPEAT_MODE_ALL && itemsList.isNotEmpty()) 0 else -1

        override fun play() = this@DesktopPlayerConnection.play()
        override fun pause() = this@DesktopPlayerConnection.pause()
        override fun seekTo(position: Long) = this@DesktopPlayerConnection.seekTo(position)

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            if (mediaItemIndex == currentMediaItemIndex && isPlaying) {
                this@DesktopPlayerConnection.seekTo(positionMs)
            } else {
                playIndex(mediaItemIndex, positionMs.coerceAtLeast(0L))
            }
        }

        override fun seekToDefaultPosition(mediaItemIndex: Int) {
            playIndex(mediaItemIndex, 0L)
        }

        override fun seekToNext() = this@DesktopPlayerConnection.seekToNext()
        override fun seekToPrevious() = this@DesktopPlayerConnection.seekToPrevious()
        override fun seekToPreviousMediaItem() = this@DesktopPlayerConnection.seekToPrevious()
        override fun seekToNextMediaItem() = this@DesktopPlayerConnection.seekToNext()

        override fun setRepeatMode(repeatMode: Int) {
            this@DesktopPlayerConnection.repeatMode.value = repeatMode
            updateSkipFlags()
        }

        override fun getMediaItemAt(index: Int): MediaItem = itemsList.getOrElse(index) { MediaItem() }

        override fun addMediaItem(mediaItem: MediaItem) {
            itemsList.add(mediaItem)
            rebuildWindows()
        }

        override fun addMediaItem(index: Int, mediaItem: MediaItem) {
            itemsList.add(index.coerceIn(0, itemsList.size), mediaItem)
            if (index <= currentMediaItemIndex && itemsList.size > 1) {
                this@DesktopPlayerConnection.currentMediaItemIndex.value += 1
                this@DesktopPlayerConnection.currentWindowIndex.value += 1
            }
            rebuildWindows()
        }

        override fun addMediaItems(mediaItems: List<MediaItem>) {
            itemsList.addAll(mediaItems)
            rebuildWindows()
        }

        override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
            val safeIndex = index.coerceIn(0, itemsList.size)
            itemsList.addAll(safeIndex, mediaItems)
            if (safeIndex <= currentMediaItemIndex && itemsList.size > mediaItems.size) {
                this@DesktopPlayerConnection.currentMediaItemIndex.value += mediaItems.size
                this@DesktopPlayerConnection.currentWindowIndex.value += mediaItems.size
            }
            rebuildWindows()
        }

        override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
            if (currentIndex !in itemsList.indices || newIndex !in itemsList.indices || currentIndex == newIndex) return
            val item = itemsList.removeAt(currentIndex)
            itemsList.add(newIndex, item)
            val cur = currentMediaItemIndex
            val updatedCur = when {
                cur == currentIndex -> newIndex
                currentIndex < cur && newIndex >= cur -> cur - 1
                currentIndex > cur && newIndex <= cur -> cur + 1
                else -> cur
            }
            this@DesktopPlayerConnection.currentMediaItemIndex.value = updatedCur
            this@DesktopPlayerConnection.currentWindowIndex.value = updatedCur
            rebuildWindows()
        }

        override fun removeMediaItem(index: Int) {
            if (index !in itemsList.indices) return
            itemsList.removeAt(index)
            if (itemsList.isEmpty()) {
                stop()
                clearMediaItems()
                return
            }
            val cur = currentMediaItemIndex
            if (index < cur) {
                this@DesktopPlayerConnection.currentMediaItemIndex.value = cur - 1
                this@DesktopPlayerConnection.currentWindowIndex.value = cur - 1
            } else if (index == cur) {
                val nextIdx = cur.coerceAtMost(itemsList.lastIndex)
                rebuildWindows()
                playIndex(nextIdx, 0L)
                return
            }
            rebuildWindows()
        }

        override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
            for (i in (toIndex - 1) downTo fromIndex) {
                if (i in itemsList.indices) itemsList.removeAt(i)
            }
            rebuildWindows()
        }

        override fun clearMediaItems() {
            itemsList.clear()
            this@DesktopPlayerConnection.currentMediaItemIndex.value = 0
            this@DesktopPlayerConnection.currentWindowIndex.value = 0
            this@DesktopPlayerConnection.mediaMetadata.value = null
            rebuildWindows()
        }

        override fun prepare() {
            if (playbackState == STATE_IDLE && itemsList.isNotEmpty()) {
                playIndex(currentMediaItemIndex, currentPosition)
            }
        }

        override fun stop() {
            runCatching { vlcPlayer?.controls()?.stop() }
            this@DesktopPlayerConnection.isPlaying.value = false
            this@DesktopPlayerConnection.playbackState.value = STATE_IDLE
        }

        override fun addListener(listener: Any?) {
            if (listener is Listener) listeners.add(listener)
        }

        override fun removeListener(listener: Any?) {
            if (listener is Listener) listeners.remove(listener)
        }

        internal fun setItemsInternal(items: List<MediaItem>, startIndex: Int) {
            itemsList.clear()
            itemsList.addAll(items)
            updateCurrentIndexInternal(startIndex)
            rebuildWindows()
        }

        internal fun updateCurrentIndexInternal(index: Int) {
            this@DesktopPlayerConnection.currentMediaItemIndex.value = index
            this@DesktopPlayerConnection.currentWindowIndex.value = index
            updateSkipFlags()
        }

        private fun rebuildWindows() {
            val windows = itemsList.mapIndexed { idx, mediaItem ->
                Timeline.Window(
                    uid = "${mediaItem.mediaId}_$idx",
                    mediaItem = mediaItem,
                    firstPeriodIndex = idx,
                    lastPeriodIndex = idx,
                )
            }
            queueWindows.value = windows
            updateSkipFlags()
            val tl = Timeline(windows)
            listeners.forEach { it.onTimelineChanged(tl, 0) }
        }

        private fun updateSkipFlags() {
            val count = itemsList.size
            val idx = currentMediaItemIndex
            val repAll = repeatMode == REPEAT_MODE_ALL
            canSkipPrevious.value = count > 0 && (idx > 0 || repAll)
            canSkipNext.value = count > 0 && (idx + 1 < count || repAll)
        }

        internal fun notifyPlaybackState(state: Int) {
            listeners.forEach { it.onPlaybackStateChanged(state) }
        }

        internal fun notifyIsPlaying(playing: Boolean) {
            listeners.forEach { it.onIsPlayingChanged(playing) }
        }

        internal fun notifyMediaItemTransition(item: MediaItem?, reason: Int) {
            listeners.forEach { it.onMediaItemTransition(item, reason) }
        }
    }

    private data class ResolvedStreamInfo(
        val videoId: String,
        val streamUrl: String,
        val headers: Map<String, String>,
        val mimeType: String,
        val contentLength: Long?,
        val durationMs: Long,
        val clientName: String,
        val expiresAtMs: Long,
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAtMs - 30_000L
    }

    private inner class DesktopStreamProxy {
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
                if (videoId.isBlank()) {
                    exchange.sendResponseHeaders(404, -1)
                    exchange.close()
                    return
                }

                val info = resolvedStreams[videoId]?.takeIf { !it.isExpired() }
                    ?: runBlocking(Dispatchers.IO) { resolveStream(videoId, mediaMetadata.value).getOrNull() }
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
            } catch (_: Exception) {
                // Client disconnected (e.g. seek or track change)
            } finally {
                runCatching { exchange.close() }
            }
        }
    }
}
