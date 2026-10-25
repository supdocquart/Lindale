package com.metrolist.music.discord
import com.metrolist.music.platform.AppContext

import com.metrolist.music.constants.DiscordActivityNameKey
import com.metrolist.music.constants.DiscordActivityTypeKey
import com.metrolist.music.constants.DiscordAdvancedModeKey
import com.metrolist.music.constants.DiscordButton1EnabledKey
import com.metrolist.music.constants.DiscordButton1LabelKey
import com.metrolist.music.constants.DiscordButton1UrlKey
import com.metrolist.music.constants.DiscordButton2EnabledKey
import com.metrolist.music.constants.DiscordButton2LabelKey
import com.metrolist.music.constants.DiscordButton2UrlKey
import com.metrolist.music.constants.DiscordDetailsTemplateKey
import com.metrolist.music.constants.DiscordStateTemplateKey
import com.metrolist.music.constants.DiscordUserStatusKey
import com.metrolist.music.constants.EnableDiscordRPCKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/** Publishes the current song as Discord Rich Presence. */
class DesktopDiscordPresence(
    private val connection: PlayerConnection,
    private val database: MusicDatabase,
    private val context: AppContext = AppContext(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val ipc = DiscordIpcClient(com.metrolist.music.BuildConfig.DISCORD_APP_ID.toString())

    @Volatile
    private var enabled = true

    // DESKTOP: exponential backoff for gateway reconnects (1 minute -> 30 minutes), reset on READY.
    @Volatile
    private var reconnectBackoffMs = MIN_RECONNECT_BACKOFF_MS

    @Volatile
    private var nextReconnectAttemptAtMs = 0L

    @Volatile
    private var giveUpCooldownArmed = false

    fun start() {
        if (!DiscordRpcManager.isInitialized()) {
            DiscordRpcManager.init(context)
        }

        scope.launch {
            context.dataStore.data
                .map { it[EnableDiscordRPCKey] ?: true }
                .distinctUntilChanged()
                .collect { value ->
                    enabled = value
                    Timber.tag(TAG).i("desktopPresence: enabled=%s", value)
                    if (value) {
                        // DESKTOP: the user re-enabled the integration, start a fresh retry cycle.
                        reconnectBackoffMs = MIN_RECONNECT_BACKOFF_MS
                        nextReconnectAttemptAtMs = 0L
                        giveUpCooldownArmed = false
                        sync()
                    } else {
                        clearAll()
                    }
                }
        }

        scope.launch {
            combine(connection.mediaMetadata, connection.isPlaying) { meta, playing -> meta?.id to playing }
                .distinctUntilChanged()
                .collectLatest {
                    delay(500)
                    sync()
                }
        }

        scope.launch {
            DiscordRpcManager.connectionStatus.collect { status ->
                if (status == DiscordRpcManager.Status.Connected) {
                    // DESKTOP: a successful READY resets the reconnect backoff.
                    reconnectBackoffMs = MIN_RECONNECT_BACKOFF_MS
                    nextReconnectAttemptAtMs = 0L
                    giveUpCooldownArmed = false
                    sync()
                }
            }
        }

        scope.launch {
            DiscordRpcManager.settingsChanged.collect { sync() }
        }

        // Picks up the Discord app being started or closed while music is playing.
        // DESKTOP: gateway reconnects use an exponential backoff instead of a fixed 20 s poll.
        scope.launch {
            while (true) {
                val untilRetryMs = nextReconnectAttemptAtMs - System.currentTimeMillis()
                val waitMs = when {
                    !enabled || DiscordRpcManager.isReady() -> MIN_RECONNECT_BACKOFF_MS
                    untilRetryMs > 0L -> untilRetryMs.coerceAtMost(reconnectBackoffMs)
                    else -> MIN_RECONNECT_BACKOFF_MS
                }
                delay(waitMs)
                if (enabled && !ipc.isConnected) sync()
            }
        }
    }

    private fun clearAll() {
        ipc.clear()
        if (DiscordRpcManager.isReady()) DiscordRpcManager.clear()
    }

    private fun scheduleGatewayReconnectIfDue(token: String) {
        // DESKTOP: the gateway owns its retry loop; only start a new connect when it is idle.
        if (DiscordRpcManager.isReconnecting()) return
        val now = System.currentTimeMillis()
        if (DiscordRpcManager.isPermanentlyFailed() && !giveUpCooldownArmed) {
            // DESKTOP: after a give-up wait 1, 2, 4 ... up to 30 minutes before the next attempt.
            giveUpCooldownArmed = true
            val failedAt = DiscordRpcManager.permanentlyFailedAtMs().takeIf { it > 0L } ?: now
            nextReconnectAttemptAtMs = failedAt + reconnectBackoffMs
            reconnectBackoffMs = (reconnectBackoffMs * 2).coerceAtMost(MAX_RECONNECT_BACKOFF_MS)
        }
        if (now < nextReconnectAttemptAtMs) return
        giveUpCooldownArmed = false
        nextReconnectAttemptAtMs = now + reconnectBackoffMs
        Timber.tag(TAG).i("desktopPresence: not ready, reconnecting (backoff=%dms)", reconnectBackoffMs)
        DiscordRpcManager.reconnectWithToken(token)
    }

    private suspend fun sync() = syncMutex.withLock {
        runCatching { syncLocked() }
            .onFailure { Timber.tag(TAG).e(it, "desktopPresence: sync failed") }
    }

    private suspend fun syncLocked() {
        if (!enabled) return
        val meta = connection.mediaMetadata.value
        if (meta == null) {
            clearAll()
            return
        }

        val useIpc = ipc.ensureConnected()
        if (useIpc) {
            // Avoid a second, duplicate activity coming from the account session.
            if (DiscordRpcManager.isReady()) DiscordRpcManager.clear()
        } else if (!DiscordRpcManager.isReady()) {
            val token = DiscordRpcManager.getAccessToken()
            if (token != null) scheduleGatewayReconnectIfDue(token)
            return
        }

        val isPlaying = connection.isPlaying.value
        if (if (useIpc) ipc.isShowing(meta.id, isPlaying) else DiscordRpcManager.isShowingSong(meta.id, isPlaying)) return

        val song = database.song(meta.id).first()
        if (song == null) {
            Timber.tag(TAG).w("desktopPresence: song %s not in database yet", meta.id)
            return
        }

        val position = runCatching { connection.player.currentPosition }.getOrDefault(0L).coerceAtLeast(0L)
        val now = System.currentTimeMillis()
        val durationMs = song.song.duration.takeIf { it > 0 }?.times(1000L)
        val startTime = if (isPlaying) now - position else 0L
        val endTime = if (isPlaying && durationMs != null) now + (durationMs - position).coerceAtLeast(0L) else null

        val store = context.dataStore
        val advancedMode = store.get(DiscordAdvancedModeKey, false)
        val activity = DiscordActivityBuilder.build(
            song = song,
            artistName = song.artists.joinToString { it.name }.ifEmpty { DiscordDefaults.UNKNOWN_ARTIST },
            albumName = song.album?.title,
            artistThumbnail = song.artists.firstOrNull()?.thumbnailUrl,
            songTitle = song.song.title,
            startTimestamp = startTime,
            endTimestamp = endTime,
            advancedMode = advancedMode,
            activityType = store.get(DiscordActivityTypeKey, DiscordDefaults.ACTIVITY_TYPE).toIntOrNull()
                ?: DiscordActivity.TYPE_LISTENING,
            activityName = store.get(DiscordActivityNameKey, DiscordDefaults.ACTIVITY_NAME),
            stateTemplate = store.get(DiscordStateTemplateKey, DiscordDefaults.STATE_TEMPLATE),
            detailsTemplate = store.get(DiscordDetailsTemplateKey, DiscordDefaults.DETAILS_TEMPLATE),
            btn1Enabled = store.get(DiscordButton1EnabledKey, true),
            btn1Label = store.get(DiscordButton1LabelKey, DiscordDefaults.BUTTON1_LABEL),
            btn1Url = store.get(DiscordButton1UrlKey, DiscordDefaults.BUTTON1_URL_TEMPLATE),
            btn2Enabled = store.get(DiscordButton2EnabledKey, true),
            btn2Label = store.get(DiscordButton2LabelKey, DiscordDefaults.BUTTON2_LABEL),
            btn2Url = store.get(DiscordButton2UrlKey, DiscordDefaults.BUTTON2_URL),
        )

        val status = when (store.get(DiscordUserStatusKey, DiscordDefaults.USER_STATUS)) {
            DiscordDefaults.STATUS_IDLE -> if (advancedMode) PresenceStatus.Idle else PresenceStatus.Online
            DiscordDefaults.STATUS_DND -> if (advancedMode) PresenceStatus.Dnd else PresenceStatus.Online
            else -> PresenceStatus.Online
        }

        Timber.tag(TAG).i("desktopPresence: setActivity song=%s isPlaying=%s via=%s", song.song.title, isPlaying, if (useIpc) "ipc" else "gateway")
        if (useIpc) {
            ipc.setActivity(activity, songId = song.song.id, isPlaying = isPlaying)
        } else {
            DiscordRpcManager.setActivity(activity, songId = song.song.id, isPlaying = isPlaying, status = status)
        }
    }

    private companion object {
        const val TAG = "DiscordSvc"

        // DESKTOP: gateway reconnect backoff after a give-up (1 minute, doubling, capped at 30 minutes).
        const val MIN_RECONNECT_BACKOFF_MS = 60_000L
        const val MAX_RECONNECT_BACKOFF_MS = 30 * 60_000L
    }
}
