package com.metrolist.music.playback

import android.content.Context
import com.metrolist.lastfm.LastFM
import com.metrolist.music.constants.EnableLastFMScrobblingKey
import com.metrolist.music.constants.LastFMUseNowPlaying
import com.metrolist.music.constants.ScrobbleDelayPercentKey
import com.metrolist.music.constants.ScrobbleDelaySecondsKey
import com.metrolist.music.constants.ScrobbleMinSongDurationKey
import com.metrolist.music.utils.ScrobbleManager
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/** Desktop replacement for the Last.fm wiring in the Android MusicService. */
class DesktopScrobbler(
    private val connection: PlayerConnection,
    private val context: Context = Context(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var manager: ScrobbleManager? = null

    private data class Settings(
        val enabled: Boolean,
        val nowPlaying: Boolean,
        val delayPercent: Float,
        val minDuration: Int,
        val delaySeconds: Int,
    )

    fun start() {
        scope.launch {
            context.dataStore.data
                .map {
                    Settings(
                        enabled = it[EnableLastFMScrobblingKey] ?: false,
                        nowPlaying = it[LastFMUseNowPlaying] ?: false,
                        delayPercent = it[ScrobbleDelayPercentKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT,
                        minDuration = it[ScrobbleMinSongDurationKey] ?: LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION,
                        delaySeconds = it[ScrobbleDelaySecondsKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS,
                    )
                }
                .distinctUntilChanged()
                .collect { s -> apply(s) }
        }

        scope.launch {
            var lastSongId: String? = null
            combine(connection.mediaMetadata, connection.isPlaying) { meta, playing -> meta to playing }
                .distinctUntilChanged()
                .collect { (meta, playing) ->
                    val m = manager ?: run {
                        lastSongId = meta?.id
                        return@collect
                    }
                    val duration = runCatching { connection.player.duration }.getOrNull()?.takeIf { it > 0 }
                    if (meta?.id != lastSongId) {
                        lastSongId = meta?.id
                        m.onSongStop()
                        if (meta != null && playing) m.onSongStart(meta, duration)
                    } else {
                        m.onPlayerStateChanged(playing, meta, duration)
                    }
                }
        }
    }

    private fun apply(s: Settings) {
        if (s.enabled && manager == null) {
            Timber.i("scrobbler: enabled")
            manager = ScrobbleManager(
                scope,
                minSongDuration = s.minDuration,
                scrobbleDelayPercent = s.delayPercent,
                scrobbleDelaySeconds = s.delaySeconds,
            ).also { m ->
                m.useNowPlaying = s.nowPlaying
                val meta = connection.mediaMetadata.value
                if (meta != null && connection.isPlaying.value) {
                    m.onSongStart(meta, runCatching { connection.player.duration }.getOrNull()?.takeIf { it > 0 })
                }
            }
        } else if (!s.enabled && manager != null) {
            Timber.i("scrobbler: disabled")
            manager?.destroy()
            manager = null
        }
        manager?.let {
            it.useNowPlaying = s.nowPlaying
            it.scrobbleDelayPercent = s.delayPercent
            it.minSongDuration = s.minDuration
            it.scrobbleDelaySeconds = s.delaySeconds
        }
    }
}
