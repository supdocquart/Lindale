package com.metrolist.music.playback

import com.metrolist.music.playback.Timeline
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
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
import com.metrolist.music.constants.HistoryDuration
import com.metrolist.music.constants.LoudnessLevel
import com.metrolist.music.constants.LoudnessLevelKey
import com.metrolist.music.constants.PauseListenHistoryKey
import com.metrolist.music.constants.PauseOnMute
import com.metrolist.music.constants.PersistentQueueKey
import com.metrolist.music.constants.PersistentShuffleAcrossQueuesKey
import com.metrolist.music.constants.PreventDuplicateTracksInQueueKey
import com.metrolist.music.constants.ShufflePlaylistFirstKey
import com.metrolist.music.constants.SimilarContent
import com.metrolist.music.constants.SleepTimerCustomDaysKey
import com.metrolist.music.constants.SleepTimerDayTimesKey
import com.metrolist.music.constants.SleepTimerDefaultKey
import com.metrolist.music.constants.SleepTimerEnabledKey
import com.metrolist.music.constants.SleepTimerEndTimeKey
import com.metrolist.music.constants.SleepTimerRepeatKey
import com.metrolist.music.constants.SleepTimerStartTimeKey
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
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.normalizationGainMb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.pow
import kotlin.math.roundToInt

// DESKTOP: stop auto-skipping after this many consecutive failures to avoid spinning through a broken queue.
private const val MAX_CONSECUTIVE_PLAYBACK_ERRORS = 5

// DESKTOP: more new likes than this at once are a library sync, not something to download automatically.
private const val MAX_AUTO_DOWNLOADS_PER_LIKE = 3

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPlayerConnection(
    private val database: MusicDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) : PlayerConnection {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolvedStreams = ConcurrentHashMap<String, ResolvedStreamInfo>()
    private val streamProxy = DesktopStreamProxy(resolvedStreams) { videoId ->
        resolveStream(videoId, mediaMetadata.value?.takeIf { it.id == videoId }).getOrNull()
    }

    // DESKTOP: playback preferences cached from DataStore, like Android's MusicService does.
    @Volatile
    private var audioQualityCached: AudioQuality = AudioQuality.AUTO

    @Volatile
    private var normalizationEnabledCached: Boolean = true

    @Volatile
    private var loudnessLevelCached: LoudnessLevel = LoudnessLevel.BALANCED

    @Volatile
    private var autoSkipNextOnErrorCached: Boolean = false

    // DESKTOP: queue preferences cached from DataStore, like Android's MusicService does.
    @Volatile
    private var autoLoadMoreCached: Boolean = true

    @Volatile
    private var disableLoadMoreWhenRepeatAllCached: Boolean = false

    @Volatile
    private var autoplayCached: Boolean = true

    @Volatile
    private var autoRadioQueueCached: Boolean = true

    // DESKTOP: Player settings that used to do nothing on desktop (see where each one is read).
    @Volatile
    private var preventDuplicatesCached: Boolean = false

    @Volatile
    private var pauseOnMuteCached: Boolean = false

    @Volatile
    private var similarContentCached: Boolean = true

    @Volatile
    private var historyThresholdMs: Long = 30_000L

    @Volatile
    private var pauseListenHistoryCached: Boolean = false

    @Volatile
    private var shufflePlaylistFirstCached: Boolean = false

    // DESKTOP: mirrors upstream's cachedPersistentQueue so the queue can be saved and restored across restarts.
    @Volatile
    private var persistentQueueCached: Boolean = true

    // DESKTOP: serializes the file writes; the periodic save, track changes and release() may overlap.
    private val queueSaveLock = Any()

    // DESKTOP: guards the auto load more request so the same page is not fetched twice.
    @Volatile
    private var autoLoadMoreInFlight: Boolean = false

    // DESKTOP: linear gain for the current track, derived from its loudness metadata.
    @Volatile
    private var normalizationFactor: Double = 1.0

    @Volatile
    private var lastLoudnessDb: Double? = null

    @Volatile
    private var lastPerceptualLoudnessDb: Double? = null

    @Volatile
    private var consecutivePlaybackErrors: Int = 0

    // DESKTOP: the native player (libVLC today); every call to it goes through this interface.
    @Volatile private var engine: AudioEngine? = null

    // Declared before init {} runs initEngine(), which reads it.
    @Volatile
    private var playbackParams: PlaybackParameters = PlaybackParameters.DEFAULT

    private var activeQueue: Queue? = null
    private var playJob: Job? = null

    override val playbackState = MutableStateFlow(Player.STATE_IDLE)

    // DESKTOP: see PlaybackStateMachine.kt.
    private val generations = PlayGenerations()
    private val mutePause = MutePause()

    /** The only place that changes playbackState / isPlaying / playWhenReady / error. */
    @Synchronized
    private fun moveTo(phase: PlaybackPhase) {
        val flags = phase.flags()
        flags.state?.let {
            if (playbackState.value != it) {
                playbackState.value = it
                player.notifyPlaybackState(it)
            }
        }
        flags.isPlaying?.let {
            if (isPlaying.value != it) {
                isPlaying.value = it
                player.notifyIsPlaying(it)
            }
        }
        flags.playWhenReady?.let { player.playWhenReady = it }
        if (!flags.keepsError) error.value = flags.error
    }
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

    // DESKTOP: completed once libVLC is ready; playback waits for it instead of the whole UI.
    private val vlcReady = kotlinx.coroutines.CompletableDeferred<Unit>()

    init {
        InnerTubeXPlayer.initialize(AppContext.Default)
        // DESKTOP: downloads do not need libVLC, so make them available before it finishes starting.
        com.metrolist.music.ui.component.DownloadCenter.engine = createDownloadEngine()
        // DESKTOP: libVLC start-up can take seconds (plugin loading, antivirus scans after an update).
        // It used to run here on the UI thread before the window existed (measured 10.7 s once), so it
        // now runs on its own thread; every property above is already initialised at this point.
        Thread({
            try {
                com.metrolist.music.platform.StartupTrace.span("player:vlc_init") { initEngine() }
            } finally {
                vlcReady.complete(Unit)
            }
            applyVolume()
        }, "vlc-init").apply { isDaemon = true }.start()

        // Volume synchronization
        scope.launch {
            service.playerVolume.collect {
                applyVolume()
                onMuteStateChanged(it <= 0f || isMuted.value)
            }
        }
        scope.launch {
            AppContext.Default.dataStore.data
                .map { prefs ->
                    preventDuplicatesCached = prefs[PreventDuplicateTracksInQueueKey] ?: false
                    pauseOnMuteCached = prefs[PauseOnMute] ?: false
                    similarContentCached = prefs[SimilarContent] ?: true
                    historyThresholdMs = ((prefs[HistoryDuration] ?: 30f) * 1000f).toLong().coerceAtLeast(0L)
                    pauseListenHistoryCached = prefs[PauseListenHistoryKey] ?: false
                    shufflePlaylistFirstCached = prefs[ShufflePlaylistFirstKey] ?: false
                }
                .collect {}
        }

        // DESKTOP: cache audio quality; Android reads the same key in MusicService.
        scope.launch {
            AppContext.Default.dataStore.data
                .map { prefs ->
                    prefs[AudioQualityKey]?.let { value ->
                        if (value == "VERY_HIGH") AudioQuality.HIGH
                        else AudioQuality.entries.find { it.name == value }
                    } ?: AudioQuality.AUTO
                }
                .distinctUntilChanged()
                .collect { newQuality ->
                    val oldQuality = audioQualityCached
                    audioQualityCached = newQuality
                    if (oldQuality != newQuality) {
                        // DESKTOP: drop cached stream URLs so the next track resolves with the new quality.
                        resolvedStreams.clear()
                        Timber.d("Audio quality changed: %s -> %s; cleared stream cache", oldQuality, newQuality)
                    }
                }
        }

        // DESKTOP: cache normalization and auto-skip preferences; Android reads them in MusicService.
        scope.launch {
            AppContext.Default.dataStore.data
                .map { prefs ->
                    Triple(
                        prefs[AudioNormalizationKey] ?: true,
                        prefs[LoudnessLevelKey].toEnum(LoudnessLevel.BALANCED),
                        prefs[AutoSkipNextOnErrorKey] ?: false,
                    )
                }
                .distinctUntilChanged()
                .collect { (normalize, loudnessLevel, autoSkip) ->
                    val normalizationChanged = normalize != normalizationEnabledCached ||
                        loudnessLevel != loudnessLevelCached
                    normalizationEnabledCached = normalize
                    loudnessLevelCached = loudnessLevel
                    autoSkipNextOnErrorCached = autoSkip
                    Timber.d(
                        "Playback settings: normalize=%s level=%s autoSkipOnError=%s",
                        normalize,
                        loudnessLevel,
                        autoSkip,
                    )
                    if (normalizationChanged) {
                        // DESKTOP: apply the new gain to the current track without restarting it.
                        updateNormalizationFactor()
                        applyVolume()
                    }
                }
        }

        // DESKTOP: queue / autoplay preferences cached the same way Android's MusicService does.
        scope.launch {
            AppContext.Default.dataStore.data
                .map { it[AutoplayKey] ?: true }
                .distinctUntilChanged()
                .collect { autoplayCached = it }
        }
        scope.launch {
            AppContext.Default.dataStore.data
                .map { it[AutoLoadMoreKey] ?: true }
                .distinctUntilChanged()
                .collect { autoLoadMoreCached = it }
        }
        scope.launch {
            AppContext.Default.dataStore.data
                .map { it[DisableLoadMoreWhenRepeatAllKey] ?: false }
                .distinctUntilChanged()
                .collect { disableLoadMoreWhenRepeatAllCached = it }
        }
        scope.launch {
            AppContext.Default.dataStore.data
                .map { it[AutoRadioQueueKey] ?: true }
                .distinctUntilChanged()
                .collect {
                    autoRadioQueueCached = it
                    // DESKTOP: mirrors the auto radio queue preference Android reads when building queues.
                    Timber.d("Auto radio queue preference: %s", it)
                }
        }

        // DESKTOP: Player settings > "Download on like". Likes come from many menus and screens, so watch
        // the liked songs instead of each like button. The first list after start-up is the baseline.
        ioScope.launch {
            var known: Set<String>? = null
            AppContext.Default.dataStore.data
                .map { it[com.metrolist.music.constants.AutoDownloadOnLikeKey] ?: false }
                .distinctUntilChanged()
                .combine(database.likedSongsByRowIdAsc()) { enabled, songs -> enabled to songs }
                .collect { (enabled, songs) ->
                    val ids = songs.mapTo(HashSet()) { it.id }
                    val previous = known
                    known = ids
                    if (!enabled || previous == null) return@collect
                    val fresh = songs.filter { it.id !in previous }
                    // shortcut: a big batch is an account sync, not a click; download those by hand.
                    if (fresh.size > MAX_AUTO_DOWNLOADS_PER_LIKE) return@collect
                    fresh.forEach {
                        Timber.i("Download on like: %s", it.id)
                        com.metrolist.music.ui.component.DownloadCenter.enqueue(it.toMediaMetadata())
                    }
                }
        }

        // DESKTOP: restore the persisted queue on startup, like upstream MusicService does.
        scope.launch {
            var restoreAttempted = false
            AppContext.Default.dataStore.data
                .map { it[PersistentQueueKey] ?: true }
                .distinctUntilChanged()
                .collect { enabled ->
                    persistentQueueCached = enabled
                    if (!enabled) {
                        deletePersistedQueue()
                    } else if (!restoreAttempted) {
                        restoreAttempted = true
                        restoreQueueFromDisk()
                    }
                }
        }

        // DESKTOP: save every 15 s while playing, matching the upstream MusicService interval.
        scope.launch {
            while (isActive) {
                delay(15_000L)
                if (persistentQueueCached && isPlaying.value) {
                    ioScope.launch { saveQueueToDisk() }
                }
            }
        }

        // Position & duration polling loop while active
        scope.launch {
            var lastTick = System.nanoTime()
            while (isActive) {
                val mp = engine
                val nowTick = System.nanoTime()
                if (isPlaying.value) {
                    listenedMs += (nowTick - lastTick) / 1_000_000L
                    remoteListen?.let { if (listenedMs >= historyThresholdMs) reportRemoteListen(it, final = false) }
                }
                lastTick = nowTick
                if (mp != null && isPlaying.value) {
                    val time = mp.timeMs
                    if (time >= 0L) {
                        player.internalPosition = time
                    }
                    val length = mp.lengthMs
                    if (length > 0L) {
                        player.internalDuration = length
                    }
                }
                delay(200)
            }
        }
    }

    /** DESKTOP: Tempo & pitch / speed dialogs. Tempo changes live; an engine that cannot change pitch live is recreated at the same spot. */
    private fun applyPlaybackParameters(params: PlaybackParameters) {
        playbackParams = params
        val current = engine
        if (current == null || current.setPlaybackParameters(params.speed, params.pitch)) return
        val index = currentMediaItemIndex.value
        val position = player.currentPosition
        val wasActive = current.isPlaying || isPlaying.value
        engine = null
        current.release()
        initEngine()
        if (wasActive && index in player.itemsList.indices) playIndex(index, position)
    }

    /** DESKTOP: the only place that pushes volume to VLC, with the loudness normalization gain applied. */
    private fun applyVolume() {
        val mp = engine ?: return
        val playerVolume = service.playerVolume.value.coerceIn(0f, 1f)
        val factor = if (normalizationEnabledCached) normalizationFactor else 1.0
        val vlcVolume = (playerVolume * factor * 100).roundToInt().coerceIn(0, 200)
        runCatching { mp.setVolume(if (isMuted.value) 0 else vlcVolume) }
    }

    /** DESKTOP: gain calculation mirrors the upstream MusicService.normalizationGainMb behaviour. */
    private fun updateNormalizationFactor() {
        val gainMb = if (normalizationEnabledCached) {
            normalizationGainMb(lastLoudnessDb, lastPerceptualLoudnessDb, loudnessLevelCached.targetLufs)
        } else {
            null
        }
        normalizationFactor = gainMb?.let { 10.0.pow(it / 2000.0) } ?: 1.0
        Timber.d(
            "Normalization: enabled=%s level=%s gainMb=%s factor=%.4f",
            normalizationEnabledCached,
            loudnessLevelCached,
            gainMb,
            normalizationFactor,
        )
    }

    private fun initEngine() {
        // Experimental: tools/mpv-dene.bat starts Lindale with LINDALE_ENGINE=mpv; VLC stays the fallback.
        val wantMpv = System.getenv("LINDALE_ENGINE").equals("mpv", ignoreCase = true)
        val created = (if (wantMpv) MpvEngine.create(EngineListener(), playbackParams.speed, playbackParams.pitch) else null)
            ?: VlcEngine.create(EngineListener(), playbackParams.speed, playbackParams.pitch)
            ?: return
        Timber.i("Audio engine: %s", created.name)
        engine = created
        com.metrolist.music.ui.component.DownloadCenter.engine = createDownloadEngine()
        created.equalizer?.let { eq ->
            runCatching { com.metrolist.music.eq.EqualizerService.attach(eq) }
                .onFailure { Timber.e(it, "EQ: equalizer unavailable") }
        }
    }

    // An inner class, not a property: initEngine() runs from init {} (on its own thread) before
    // properties declared below it are initialised.
    private inner class EngineListener : AudioEngine.Listener {
        override fun onBuffering() {
            if (!isPlaying.value) moveTo(PlaybackPhase.Buffering)
        }

        override fun onPlaying() {
            // DESKTOP: a successful start breaks the auto-skip error streak.
            consecutivePlaybackErrors = 0
            // Paused -> playing (not a track change or the end of buffering), like upstream's trigger.
            if (!player.playWhenReady) startAutomaticSleepTimerIfDue()
            moveTo(PlaybackPhase.Playing)
        }

        override fun onPaused() = moveTo(PlaybackPhase.Paused)

        override fun onStopped() = moveTo(PlaybackPhase.Stopped)

        override fun onLength(ms: Long) {
            if (ms > 0L) player.internalDuration = ms
        }

        override fun onEnded() {
            // The end of a song the user already skipped away from must not move the queue again.
            if (!generations.loadedIsCurrent()) {
                Timber.d("Ignoring the end of a replaced song")
                return
            }
            val generation = generations.current
            ioScope.launch { if (generations.isCurrent(generation)) onTrackEndedNaturally() }
        }

        override fun onError(message: String) {
            if (!generations.loadedIsCurrent()) {
                Timber.d("Ignoring an error of a replaced song: %s", message)
                return
            }
            Timber.e("%s for %s", message, mediaMetadata.value?.id)
            moveTo(
                PlaybackPhase.Failed(
                    PlaybackException(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, "Playback error"),
                ),
            )

            // DESKTOP: mirror upstream AutoSkipNextOnErrorKey without looping forever on a broken queue.
            val hasNext = player.nextMediaItemIndex != -1
            if (autoSkipNextOnErrorCached && hasNext &&
                consecutivePlaybackErrors < MAX_CONSECUTIVE_PLAYBACK_ERRORS
            ) {
                consecutivePlaybackErrors++
                Timber.d(
                    "Auto-skipping to next track after playback error (%d/%d)",
                    consecutivePlaybackErrors,
                    MAX_CONSECUTIVE_PLAYBACK_ERRORS,
                )
                ioScope.launch { seekToNext() }
            } else {
                Timber.d(
                    "Staying on the errored item: autoSkipOnError=%s hasNext=%s consecutiveErrors=%d",
                    autoSkipNextOnErrorCached,
                    hasNext,
                    consecutivePlaybackErrors,
                )
            }
        }
    }

    // DESKTOP: suspend so autoplay can fetch the radio queue when the queue ends.
    private suspend fun onTrackEndedNaturally() {
        val currentMeta = mediaMetadata.value
        recordListen(endedNaturally = true)

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
                } else if (autoplayCached && similarContentCached) {
                    // DESKTOP: the queue ended; continue with the last song's radio instead of stopping.
                    if (loadRadioIntoQueue()) {
                        playIndex(currentMediaItemIndex.value + 1, 0L)
                    } else {
                        Timber.w("Autoplay radio failed for %s; ending playback", currentMeta?.id)
                        endPlayback()
                    }
                } else {
                    endPlayback()
                }
            }
        }
    }

    // DESKTOP: playback only ends when autoplay is off or the radio could not be loaded.
    private fun endPlayback() {
        moveTo(PlaybackPhase.Ended)
    }

    /**
     * DESKTOP: keeps the current song playing and replaces everything after it with its radio,
     * mirroring upstream MusicService.startRadioSeamlessly(). Returns true when the queue changed.
     */
    private suspend fun loadRadioIntoQueue(): Boolean {
        val currentMediaId = mediaMetadata.value?.id ?: return false
        val currentIndex = currentMediaItemIndex.value

        // DESKTOP: a plain videoId endpoint lets YouTube personalize the recommendations, like upstream.
        val radioQueue = YouTubeQueue(WatchEndpoint(videoId = currentMediaId))

        var newQueue: Queue? = radioQueue
        val rawRadioItems: List<MediaItem> = try {
            val status = radioQueue.getInitialStatus()
            status.title?.let { queueTitle.value = it }
            status.items.filter { it.mediaId != currentMediaId }
        } catch (e: Exception) {
            Timber.w(e, "Radio queue failed for %s; falling back to related songs", currentMediaId)
            newQueue = null
            try {
                val relatedEndpoint = YouTube.next(WatchEndpoint(videoId = currentMediaId))
                    .getOrNull()?.relatedEndpoint
                val songs = relatedEndpoint?.let { YouTube.related(it).getOrNull() }?.songs
                    ?: return false
                songs.filter { it.id != currentMediaId }.map { it.toMediaItem() }
            } catch (fallbackError: Exception) {
                Timber.e(fallbackError, "Related songs fallback failed for %s", currentMediaId)
                return false
            }
        }

        // DESKTOP: the radio used to ignore the content filters; apply the same ones the lists use.
        val hideExplicit = AppContext.Default.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = AppContext.Default.dataStore.get(HideVideoSongsKey, false)
        val radioItems = rawRadioItems
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideoSongs)

        if (radioItems.isEmpty()) return false

        newQueue?.let {
            activeQueue = it
            // DESKTOP: the radio queue has its own continuation; allow auto load more for it again.
            autoLoadMoreInFlight = false
        }
        val itemCount = player.mediaItemCount
        if (itemCount > currentIndex + 1) {
            player.removeMediaItems(currentIndex + 1, itemCount)
        }
        player.addMediaItems(currentIndex + 1, radioItems)
        Timber.d("Radio for %s: replaced queue tail with %d items", currentMediaId, radioItems.size)
        return true
    }

    /**
     * DESKTOP: history used to be written only when a song played to its very end, so songs skipped
     * after minutes never showed up. Like upstream, a song now counts once it has been listened to for
     * Player settings > "History duration" (a song that ends sooner counts when it ends).
     */
    @Volatile
    private var listenedMs = 0L

    private fun recordListen(endedNaturally: Boolean) {
        val meta = mediaMetadata.value
        val played = listenedMs
        listenedMs = 0L
        remoteListen?.let { if (played >= historyThresholdMs) reportRemoteListen(it, final = true) }
        remoteListen = null
        // Privacy > "Pause listen history" (upstream skips the event and the play time as well).
        if (pauseListenHistoryCached) return
        if (meta == null || played <= 0L) return
        if (played < historyThresholdMs && !endedNaturally) return
        database.query {
            incrementTotalPlayTime(meta.id, played)
            insert(Event(songId = meta.id, timestamp = nowLocalDateTime(), playTime = played))
        }
    }

    /**
     * DESKTOP: YouTube Music history, like upstream's YouTubeHistoryReporter. Once a song has been
     * listened to for the history duration, YouTube is told about it through a fresh, account-bound
     * tracking request (so downloaded songs count too); the account's history and recommendations
     * then include what was played here. Needs a signed-in account; off while listen history is
     * paused. A failed report gets one more try when the song ends, with the same playback nonce.
     */
    private class RemoteListen(val videoId: String, val nonce: String) {
        @Volatile var attempts = 0
        @Volatile var inFlight = false
        @Volatile var sent = false
    }

    @Volatile
    private var remoteListen: RemoteListen? = null

    private fun reportRemoteListen(listen: RemoteListen, final: Boolean) {
        if (pauseListenHistoryCached || YouTube.cookie.isNullOrBlank()) return
        val allowed = if (final) 2 else 1
        if (listen.sent || listen.inFlight || listen.attempts >= allowed) return
        listen.attempts = allowed
        listen.inFlight = true
        ioScope.launch {
            try {
                listen.sent = YouTube.registerPlayback(listen.videoId, listen.nonce) { !pauseListenHistoryCached }
                    .onFailure { Timber.w("YouTube history: report failed (%s)", it::class.simpleName) }
                    .getOrDefault(false)
                if (listen.sent) Timber.d("YouTube history: reported %s", listen.videoId)
            } finally {
                listen.inFlight = false
            }
        }
    }

    /**
     * DESKTOP: Player settings > automatic sleep timer, ported from upstream PlayerConnection: when
     * music starts inside the configured days and time window, the sleep timer starts with the
     * default length. Windows may run past midnight (22:00-06:00).
     */
    private fun startAutomaticSleepTimerIfDue() {
        scope.launch {
            val minutes = withContext(Dispatchers.IO) { runCatching { automaticSleepTimerMinutes() }.getOrNull() }
            if (minutes != null && minutes > 0 && !service.sleepTimer.isActive) {
                Timber.i("Automatic sleep timer started: %d minutes", minutes)
                service.sleepTimer.start(minutes)
            }
        }
    }

    private fun automaticSleepTimerMinutes(): Int? {
        val store = AppContext.Default.dataStore
        if (!store.get(SleepTimerEnabledKey, false) || service.sleepTimer.isActive) return null
        val repeat = store.get(SleepTimerRepeatKey, "daily")
        val start = store.get(SleepTimerStartTimeKey, "09:00")
        val end = store.get(SleepTimerEndTimeKey, "23:00")
        val minutes = store.get(SleepTimerDefaultKey, 30f).roundToInt()
        val customDays = store.get(SleepTimerCustomDaysKey, "0,1,2,3,4")
        val dayTimes = store.get(SleepTimerDayTimesKey, "")

        val now = java.time.LocalTime.now()
        // 0 = Monday ... 6 = Sunday, as in upstream.
        val day = java.time.LocalDate.now().dayOfWeek.value - 1
        val dayAllowed = when (repeat) {
            "daily", "weekdays_weekends" -> true
            "weekdays" -> day in 0..4
            "weekends" -> day in 5..6
            "custom" -> day in customDays.split(",").mapNotNull { it.trim().toIntOrNull() }
            else -> false
        }
        if (!dayAllowed) return null

        // "daily" uses the single window; the other modes keep a window per day ("day=HH:mm-HH:mm;...").
        val perDay = dayTimes.split(";").mapNotNull { entry ->
            val (d, range) = entry.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
            val times = range.split("-").takeIf { it.size == 2 } ?: return@mapNotNull null
            d.toIntOrNull()?.let { it to (times[0] to times[1]) }
        }.toMap()
        val (from, to) = if (repeat != "daily") perDay[day] ?: (start to end) else start to end
        val format = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val startTime = java.time.LocalTime.parse(from, format)
        val endTime = java.time.LocalTime.parse(to, format)
        val inWindow = if (endTime.isAfter(startTime)) {
            now.isAfter(startTime) && now.isBefore(endTime)
        } else {
            now.isAfter(startTime) || now.isBefore(endTime)
        }
        return minutes.takeIf { inWindow }
    }

    /** DESKTOP: Player settings > "Pause music when media is muted"; resumes when unmuted (see [MutePause]). */
    private fun onMuteStateChanged(muted: Boolean) {
        if (muted) {
            // Straight to the engine: pause() is a user action and would forget the mute-pause.
            if (mutePause.onMute(pauseOnMuteCached, isPlaying.value)) engine?.pause()
        } else if (mutePause.onUnmute()) {
            play()
        }
    }

    /**
     * DESKTOP: Player settings > "Prevent duplicate tracks in queue": a song that is already queued is
     * moved instead of added twice (the song that is playing right now is left where it is).
     */
    private fun withoutQueuedDuplicates(items: List<MediaItem>): List<MediaItem> {
        if (!preventDuplicatesCached) return items
        val playing = player.itemsList.getOrNull(currentMediaItemIndex.value)?.mediaId
        val ids = items.map { it.mediaId }.toSet()
        for (i in player.itemsList.indices.reversed()) {
            val id = player.itemsList[i].mediaId
            if (id in ids && i != currentMediaItemIndex.value) player.removeMediaItem(i)
        }
        return items.filter { it.mediaId != playing }
    }

    /**
     * DESKTOP: shuffle used to only flip a flag, so next, previous and the end of a song still went
     * down the list in order. Turning it on now reorders the real queue: the current song stays and
     * everything else is shuffled, so playback and the queue screen agree. Turning it off puts the
     * songs back in the order they had (songs added meanwhile go to the end).
     */
    private var unshuffledOrder: List<MediaItem>? = null

    private fun applyShuffle(enabled: Boolean) {
        if (shuffleModeEnabled.value == enabled) return
        shuffleModeEnabled.value = enabled
        val items = player.itemsList
        if (items.isEmpty()) {
            unshuffledOrder = null
            return
        }
        val current = items[currentMediaItemIndex.value.coerceIn(items.indices)]
        if (enabled) {
            unshuffledOrder = items.toList()
            player.setItemsInternal(shuffledAround(items, current), 0)
        } else {
            val original = unshuffledOrder
            unshuffledOrder = null
            if (original == null) return
            val remaining = items.toMutableList()
            val restored = original.mapNotNull { o ->
                val i = remaining.indexOfFirst { it === o }
                if (i >= 0) remaining.removeAt(i) else null
            } + remaining
            player.setItemsInternal(restored, restored.indexOfFirst { it === current }.coerceAtLeast(0))
        }
        Timber.d("Shuffle %s: %d items", if (enabled) "on" else "off", items.size)
    }

    /** [current] first, then every other item in random order. */
    private fun shuffledAround(items: List<MediaItem>, current: MediaItem): List<MediaItem> {
        val rest = items.filterNot { it === current }
        val original = originalQueueItems
        // Player settings > "Shuffle playlist or album first": songs added after the queue started
        // (autoplay radio, "add to queue") are shuffled after the playlist's own songs, as upstream does.
        if (!shufflePlaylistFirstCached || original.isEmpty()) return listOf(current) + rest.shuffled()
        val (fromList, added) = rest.partition { it in original }
        return listOf(current) + fromList.shuffled() + added.shuffled()
    }

    /** The items the current queue started with (by identity), for "shuffle playlist first". */
    private var originalQueueItems: Set<MediaItem> = emptySet()

    private fun identitySetOf(items: List<MediaItem>): Set<MediaItem> =
        java.util.Collections.newSetFromMap(java.util.IdentityHashMap<MediaItem, Boolean>()).apply { addAll(items) }

    override fun playQueue(queue: Queue) {
        activeQueue = queue
        // DESKTOP: a new queue starts with a clean auto load more state.
        autoLoadMoreInFlight = false
        playJob?.cancel()
        playJob = ioScope.launch {
            try {
                moveTo(PlaybackPhase.Buffering)
                queue.preloadItem?.let { preload ->
                    mediaMetadata.value = preload
                    player.internalDuration = (preload.duration.takeIf { it > 0 } ?: 0) * 1000L
                }

                val status = queue.getInitialStatus()
                queueTitle.value = status.title
                val items = status.items
                if (items.isEmpty()) {
                    moveTo(PlaybackPhase.Idle)
                    return@launch
                }

                var startIndex = status.mediaItemIndex.coerceIn(0, items.lastIndex)
                unshuffledOrder = null
                originalQueueItems = identitySetOf(items)
                if (shuffleModeEnabled.value) {
                    // DESKTOP: like upstream, a new queue turns shuffle off unless
                    // Player settings > "Persistent shuffle" keeps it on (then the new queue is shuffled).
                    if (AppContext.Default.dataStore.get(PersistentShuffleAcrossQueuesKey, false)) {
                        unshuffledOrder = items
                        player.setItemsInternal(shuffledAround(items, items[startIndex]), 0)
                        startIndex = 0
                    } else {
                        shuffleModeEnabled.value = false
                        player.setItemsInternal(items, startIndex)
                    }
                } else {
                    player.setItemsInternal(items, startIndex)
                }
                playIndex(startIndex, status.position)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "Failed to load queue")
                moveTo(
                    PlaybackPhase.Failed(
                        PlaybackException(PlaybackException.ERROR_CODE_REMOTE_ERROR, e.message ?: "Failed to load queue", e),
                    ),
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
        recordListen(endedNaturally = false)
        remoteListen = RemoteListen(items[index].mediaId, generateClientPlaybackNonce())
        val generation = generations.next()
        // A new song is the user's (or the queue's) choice: unmuting must not resume a mute-pause.
        mutePause.onUserAction()

        player.updateCurrentIndexInternal(index)
        val item = items[index]
        val meta = item.metadata
        mediaMetadata.value = meta
        // DESKTOP: reset normalization until the new stream's loudness metadata is known.
        lastLoudnessDb = null
        lastPerceptualLoudnessDb = null
        updateNormalizationFactor()
        player.internalPosition = startPositionMs
        player.internalDuration = ((meta?.duration?.takeIf { it > 0 } ?: 0) * 1000L)
        moveTo(PlaybackPhase.Buffering)
        player.notifyMediaItemTransition(item, 2) // MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED / SEEK

        // DESKTOP: upstream saves the queue on every media item transition.
        if (persistentQueueCached) {
            ioScope.launch { saveQueueToDisk() }
        }

        if (meta != null) {
            ioScope.launch {
                runCatching {
                    database.query {
                        insert(meta)
                    }
                }
            }
        }

        // DESKTOP: auto load more, mirroring upstream MusicService.onMediaItemTransition.
        val q = activeQueue
        if (autoLoadMoreCached &&
            q != null &&
            index >= items.size - 5 &&
            q.hasNextPage() &&
            !(disableLoadMoreWhenRepeatAllCached && repeatMode.value == Player.REPEAT_MODE_ALL) &&
            !autoLoadMoreInFlight
        ) {
            autoLoadMoreInFlight = true
            ioScope.launch {
                try {
                    val more = q.nextPage()
                    if (more.isNotEmpty() && activeQueue === q && playbackState.value != Player.STATE_IDLE) {
                        player.addMediaItems(more)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load the next queue page")
                } finally {
                    autoLoadMoreInFlight = false
                }
            }
        }

        playJob?.cancel()
        playJob = ioScope.launch {
            val videoId = item.mediaId.ifBlank { meta?.id.orEmpty() }
            if (videoId.isBlank()) return@launch

            // DESKTOP: libVLC is initialised in the background; wait for it before touching the player.
            vlcReady.await()

            val localFile = com.metrolist.music.ui.component.DownloadCenter.localFile(videoId)
            if (localFile != null) {
                val mp = engine
                if (mp != null) {
                    if (!generations.load(generation)) return@launch
                    Timber.i("Playing downloaded file for %s", videoId)
                    applyVolume()
                    mp.load(localFile.absolutePath, startPositionMs)
                    return@launch
                }
            }

            val streamResult = resolveStream(videoId, meta)
            // The user may have moved on while the stream was being resolved: keep the cache, drop the rest.
            if (!generations.isCurrent(generation)) {
                streamResult.onSuccess { resolvedStreams[videoId] = it }
                Timber.d("Dropping the stream of a replaced song (%s)", videoId)
                return@launch
            }
            streamResult.onSuccess { resolved ->
                resolvedStreams[videoId] = resolved
                currentStreamClient.value = resolved.clientName
                if (resolved.durationMs > 0 && player.internalDuration <= 0L) {
                    player.internalDuration = resolved.durationMs
                }

                // DESKTOP: normalization gain for this stream, matching upstream MusicService.
                lastLoudnessDb = resolved.loudnessDb
                lastPerceptualLoudnessDb = resolved.perceptualLoudnessDb
                updateNormalizationFactor()

                val proxyUrl = streamProxy.getStreamUrl(videoId)
                val mp = engine
                if (mp != null && generations.load(generation)) {
                    applyVolume()
                    mp.load(proxyUrl, startPositionMs)
                }
            }.onFailure { err ->
                Timber.e(err, "Failed to resolve stream for $videoId")
                moveTo(
                    PlaybackPhase.Failed(
                        PlaybackException(PlaybackException.ERROR_CODE_REMOTE_ERROR, err.message ?: "Failed to resolve stream", err),
                    ),
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
            audioQuality = audioQualityCached,
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
                    loudnessDb = data.audioConfig?.loudnessDb,
                    perceptualLoudnessDb = data.audioConfig?.perceptualLoudnessDb,
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
                    loudnessDb = response.playerConfig?.audioConfig?.loudnessDb,
                    perceptualLoudnessDb = response.playerConfig?.audioConfig?.perceptualLoudnessDb,
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
        mutePause.onUserAction()
        // DESKTOP: play pressed while libVLC is still starting: playIndex waits for it.
        val mp = engine ?: run {
            if (player.mediaItemCount > 0) playIndex(currentMediaItemIndex.value, player.currentPosition)
            return
        }
        // A fresh VLC player (app start, or rebuilt after a pitch change) has no media loaded yet.
        val hasMedia = mp.hasMedia
        if (!hasMedia || playbackState.value == Player.STATE_IDLE || playbackState.value == Player.STATE_ENDED) {
            if (player.mediaItemCount > 0) {
                playIndex(currentMediaItemIndex.value, player.currentPosition)
            }
        } else {
            mp.play()
        }
    }

    override fun pause() {
        mutePause.onUserAction()
        engine?.pause()
    }

    override fun seekTo(position: Long) {
        val target = position.coerceAtLeast(0L)
        player.internalPosition = target
        engine?.seek(target)
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
        @Suppress("NAME_SHADOWING")
        val items = withoutQueuedDuplicates(items)
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
        @Suppress("NAME_SHADOWING")
        val items = withoutQueuedDuplicates(items)
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
        applyVolume()
        onMuteStateChanged(muted || service.playerVolume.value <= 0f)
    }

    override fun toggleRepeatMode() {
        player.toggleRepeatMode()
    }

    override fun startRadioSeamlessly() {
        // DESKTOP: keep the current song playing and replace the rest of the queue with its radio.
        ioScope.launch {
            if (!loadRadioIntoQueue()) {
                Timber.w("startRadioSeamlessly: no radio items for %s", mediaMetadata.value?.id)
            }
        }
    }

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

    /**
     * DESKTOP: mirrors upstream MusicService.saveQueueToDisk(). Both files are written through a temp
     * file and renamed so an interrupted write can never leave a half-written queue behind.
     */
    private fun saveQueueToDisk() {
        if (!persistentQueueCached) {
            deletePersistedQueue()
            return
        }

        synchronized(queueSaveLock) {
            try {
                val items = player.itemsList
                if (items.isEmpty()) {
                    // DESKTOP: upstream keeps the previous file here, which would resurrect a queue the
                    // user cleared. An empty queue means there is nothing left to restore.
                    Timber.d("Skipping queue save - no media items")
                    deletePersistedQueue()
                    return
                }

                val metadata = items.mapNotNull { it.metadata }
                if (metadata.isEmpty()) {
                    Timber.d("Skipping queue save - no metadata")
                    return
                }

                val index = currentMediaItemIndex.value.coerceIn(0, metadata.lastIndex)
                // DESKTOP: with shuffle on, also keep the order to go back to, as indexes into items.
                val unshuffled = unshuffledOrder?.takeIf { metadata.size == items.size }?.let { order ->
                    val positions = java.util.IdentityHashMap<MediaItem, Int>().apply { items.forEachIndexed { i, item -> put(item, i) } }
                    order.mapNotNull { positions[it] }
                }
                val persistQueue = PersistedQueueSnapshot(
                    title = queueTitle.value,
                    items = metadata.map { it.toPersistedMetadata() },
                    mediaItemIndex = index,
                    position = player.currentPosition.coerceAtLeast(0L),
                    unshuffledOrder = unshuffled,
                )
                val persistPlayerState = PersistPlayerState(
                    playWhenReady = player.playWhenReady,
                    repeatMode = repeatMode.value,
                    shuffleModeEnabled = shuffleModeEnabled.value,
                    volume = service.playerVolume.value,
                    currentPosition = player.currentPosition.coerceAtLeast(0L),
                    currentMediaItemIndex = currentMediaItemIndex.value,
                    playbackState = playbackState.value,
                )

                val filesDir = AppContext.Default.filesDir
                writeObjectAtomically(filesDir.resolve(PlaybackConstants.PERSISTENT_QUEUE_FILE), persistQueue)
                writeObjectAtomically(filesDir.resolve(PlaybackConstants.PERSISTENT_PLAYER_STATE_FILE), persistPlayerState)
                Timber.d(
                    "Persisted queue: %d items, index=%d, position=%d ms",
                    persistQueue.items.size,
                    index,
                    persistQueue.position,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to persist the queue")
            }
        }
    }

    /**
     * DESKTOP: restores the queue in a paused state. Nothing is resolved or played until the user
     * presses play, which then goes through the normal playIndex() stream resolution.
     */
    private fun restoreQueueFromDisk() {
        if (player.mediaItemCount > 0) return

        val filesDir = AppContext.Default.filesDir
        val queueFile = filesDir.resolve(PlaybackConstants.PERSISTENT_QUEUE_FILE)
        if (!queueFile.exists()) return

        val persistQueue = runCatching {
            queueObjectInput(queueFile).use { it.readObject() as PersistedQueueSnapshot }
        }.getOrElse { e ->
            Timber.w(e, "Failed to read the persisted queue; clearing it")
            deletePersistedQueue()
            return
        }

        val items = persistQueue.items.map { it.toMediaMetadata().toMediaItem() }
        if (items.isEmpty()) {
            Timber.w("Persisted queue had no items; clearing it")
            deletePersistedQueue()
            return
        }

        val index = persistQueue.mediaItemIndex.coerceIn(0, items.lastIndex)
        val position = persistQueue.position.coerceAtLeast(0L)
        player.setItemsInternal(items, index)
        // DESKTOP: a shuffled queue can still be put back in order after a restart. Queues saved by
        // older versions have no order (null).
        unshuffledOrder = persistQueue.unshuffledOrder
            ?.takeIf { order -> order.all { it in items.indices } && order.toSet().size == order.size }
            ?.map { items[it] }
        activeQueue = ListQueue(
            title = persistQueue.title,
            items = items,
            startIndex = index,
            position = position,
        )
        queueTitle.value = persistQueue.title

        val item = items[index]
        mediaMetadata.value = item.metadata
        // DESKTOP: the restored stream is unknown until play, so normalization starts neutral.
        lastLoudnessDb = null
        lastPerceptualLoudnessDb = null
        updateNormalizationFactor()
        player.internalPosition = position
        player.internalDuration = ((item.metadata?.duration?.takeIf { it > 0 } ?: 0) * 1000L)
        moveTo(PlaybackPhase.Paused)
        Timber.d("Restored persisted queue: %d items, index=%d, position=%d ms", items.size, index, position)

        restorePlayerStateFromDisk(index)
    }

    // DESKTOP: upstream restores volume and position from the state file; see the note about repeat/shuffle.
    private fun restorePlayerStateFromDisk(queueIndex: Int) {
        val stateFile = AppContext.Default.filesDir.resolve(PlaybackConstants.PERSISTENT_PLAYER_STATE_FILE)
        if (!stateFile.exists()) return

        val state = runCatching {
            queueObjectInput(stateFile).use { it.readObject() as PersistPlayerState }
        }.getOrElse { e ->
            Timber.w(e, "Failed to read the persisted player state; clearing it")
            runCatching { stateFile.delete() }
            return
        }

        service.playerVolume.value = state.volume.coerceIn(0f, 1f)
        // DESKTOP: upstream keeps repeat/shuffle in DataStore and skips them here; the desktop port
        // has no such startup source, so the persisted values are restored instead.
        // Player settings > "Remember shuffle and repeat" (on by default).
        val rememberModes = runCatching {
            kotlinx.coroutines.runBlocking {
                AppContext.Default.dataStore.data.first()[com.metrolist.music.constants.RememberShuffleAndRepeatKey]
            }
        }.getOrNull() ?: true
        if (rememberModes) {
            player.setRepeatMode(state.repeatMode)
            // The saved queue is already in shuffled order; only the flag comes back.
            shuffleModeEnabled.value = state.shuffleModeEnabled
        }
        if (state.currentMediaItemIndex == queueIndex) {
            player.internalPosition = state.currentPosition.coerceAtLeast(0L)
        }
        Timber.d(
            "Restored persisted player state: volume=%.2f repeat=%d shuffle=%s index=%d position=%d ms",
            state.volume,
            state.repeatMode,
            state.shuffleModeEnabled,
            state.currentMediaItemIndex,
            state.currentPosition,
        )
    }

    private fun deletePersistedQueue() {
        synchronized(queueSaveLock) {
            val filesDir = AppContext.Default.filesDir
            val queueDeleted = runCatching {
                filesDir.resolve(PlaybackConstants.PERSISTENT_QUEUE_FILE).delete()
            }.getOrDefault(false)
            val stateDeleted = runCatching {
                filesDir.resolve(PlaybackConstants.PERSISTENT_PLAYER_STATE_FILE).delete()
            }.getOrDefault(false)
            if (queueDeleted || stateDeleted) {
                Timber.d("Deleted persisted queue files")
            }
        }
    }

    // DESKTOP: temp file + atomic rename keeps the previous save intact if the process dies mid-write.
    // DESKTOP: only our own snapshot classes and basic JDK/Kotlin types may be deserialized from disk.
    private fun queueObjectInput(file: File): ObjectInputStream =
        ObjectInputStream(file.inputStream().buffered()).apply {
            objectInputFilter = java.io.ObjectInputFilter.Config.createFilter(
                "maxdepth=20;maxrefs=200000;maxbytes=67108864;com.metrolist.music.**;java.lang.*;java.util.*;kotlin.**;!*",
            )
        }

    private fun writeObjectAtomically(target: File, value: Any) {
        val directory = target.parentFile ?: AppContext.Default.filesDir
        directory.mkdirs()
        val temp = File(directory, "${target.name}.tmp")
        try {
            ObjectOutputStream(temp.outputStream().buffered()).use { it.writeObject(value) }
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            runCatching { temp.delete() }
        }
    }

    fun release() {
        // DESKTOP: runs on app close, so the last queue and position are written synchronously.
        recordListen(endedNaturally = false)
        saveQueueToDisk()
        playJob?.cancel()
        engine?.release()
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

        override var playbackParameters: PlaybackParameters
            get() = this@DesktopPlayerConnection.playbackParams
            set(value) = this@DesktopPlayerConnection.applyPlaybackParameters(value)

        override val duration: Long
            get() = internalDuration

        override val repeatMode: Int
            get() = this@DesktopPlayerConnection.repeatMode.value

        override var shuffleModeEnabled: Boolean
            get() = this@DesktopPlayerConnection.shuffleModeEnabled.value
            set(value) {
                applyShuffle(value)
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
            runCatching { engine?.stop() }
            moveTo(PlaybackPhase.Idle)
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

}
