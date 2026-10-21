package com.metrolist.music.discord

import android.content.Context
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

/** Desktop replacement for the Discord presence logic that lives in the Android MusicService. */
class DesktopDiscordPresence(
    private val connection: PlayerConnection,
    private val database: MusicDatabase,
    private val context: Context = Context(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    @Volatile
    private var enabled = true

    @Volatile
    private var lastReconnectAttemptAtMs = 0L

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
                    if (value) sync() else if (DiscordRpcManager.isReady()) DiscordRpcManager.clear()
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
                if (status == DiscordRpcManager.Status.Connected) sync()
            }
        }

        scope.launch {
            DiscordRpcManager.settingsChanged.collect { sync() }
        }
    }

    private suspend fun sync() = syncMutex.withLock {
        runCatching { syncLocked() }
            .onFailure { Timber.tag(TAG).e(it, "desktopPresence: sync failed") }
    }

    private suspend fun syncLocked() {
        if (!enabled) return
        val meta = connection.mediaMetadata.value
        if (meta == null) {
            if (DiscordRpcManager.isReady()) DiscordRpcManager.clear()
            return
        }

        if (!DiscordRpcManager.isReady()) {
            val token = DiscordRpcManager.getAccessToken()
            val now = System.currentTimeMillis()
            if (token != null && now - lastReconnectAttemptAtMs > 30_000L) {
                lastReconnectAttemptAtMs = now
                Timber.tag(TAG).i("desktopPresence: not ready, reconnecting")
                DiscordRpcManager.reconnectWithToken(token)
            }
            return
        }

        val isPlaying = connection.isPlaying.value
        if (DiscordRpcManager.isShowingSong(meta.id, isPlaying)) return

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

        Timber.tag(TAG).i("desktopPresence: setActivity song=%s isPlaying=%s", song.song.title, isPlaying)
        DiscordRpcManager.setActivity(activity, songId = song.song.id, isPlaying = isPlaying, status = status)
    }

    private companion object {
        const val TAG = "DiscordSvc"
    }
}
