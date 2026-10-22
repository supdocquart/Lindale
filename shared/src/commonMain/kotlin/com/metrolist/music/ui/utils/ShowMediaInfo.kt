package com.metrolist.music.ui.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.MediaInfo
import com.metrolist.music.LocalArtistNameAliases
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.constants.LoudnessLevel
import com.metrolist.music.constants.LoudnessLevelKey
import com.metrolist.music.db.entities.FormatEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.platform.AppContext
import com.metrolist.music.platform.DesktopShare
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.utils.ArtistNameAliases
import com.metrolist.music.utils.rememberEnumPreference
import metrolist.shared.generated.resources.Res
import metrolist.shared.generated.resources.bitrate
import metrolist.shared.generated.resources.codecs
import metrolist.shared.generated.resources.content_copy
import metrolist.shared.generated.resources.contrast
import metrolist.shared.generated.resources.description
import metrolist.shared.generated.resources.dislikes
import metrolist.shared.generated.resources.favorite
import metrolist.shared.generated.resources.favorite_border
import metrolist.shared.generated.resources.file_size
import metrolist.shared.generated.resources.general
import metrolist.shared.generated.resources.gradient
import metrolist.shared.generated.resources.info
import metrolist.shared.generated.resources.information
import metrolist.shared.generated.resources.key
import metrolist.shared.generated.resources.likes
import metrolist.shared.generated.resources.link
import metrolist.shared.generated.resources.loudness
import metrolist.shared.generated.resources.loudness_level
import metrolist.shared.generated.resources.media_id
import metrolist.shared.generated.resources.mime_type
import metrolist.shared.generated.resources.music_note
import metrolist.shared.generated.resources.person
import metrolist.shared.generated.resources.play
import metrolist.shared.generated.resources.radio
import metrolist.shared.generated.resources.sample_rate
import metrolist.shared.generated.resources.song_artists
import metrolist.shared.generated.resources.song_title
import metrolist.shared.generated.resources.stream_client
import metrolist.shared.generated.resources.unknown
import metrolist.shared.generated.resources.views
import metrolist.shared.generated.resources.volume
import metrolist.shared.generated.resources.volume_mute
import metrolist.shared.generated.resources.volume_up
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

// DESKTOP: port of upstream ShowMediaInfo (clipboard/toast via DesktopShare, scrollable Column
// instead of a LazyColumn so it can live inside the details dialog).

fun AppContext.showMediaInfo(mediaId: String) {}

@Composable
fun ShowMediaInfo(videoId: String) {
    if (videoId.isBlank()) return

    var info by remember(videoId) { mutableStateOf<MediaInfo?>(null) }
    var song by remember(videoId) { mutableStateOf<Song?>(null) }
    var currentFormat by remember(videoId) { mutableStateOf<FormatEntity?>(null) }

    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val currentStreamClient by playerConnection?.currentStreamClient?.collectAsState() ?: remember { mutableStateOf(null) }
    val playerVolume by playerConnection?.service?.playerVolume?.collectAsState() ?: remember { mutableStateOf(1f) }
    val artistNameAliases = LocalArtistNameAliases.current
    val loudnessLevel by rememberEnumPreference(LoudnessLevelKey, defaultValue = LoudnessLevel.BALANCED)
    val targetLufs = loudnessLevel.targetLufs

    LaunchedEffect(videoId) { info = YouTube.getMediaInfo(videoId).getOrNull() }
    LaunchedEffect(videoId) { database.song(videoId).collect { song = it } }
    LaunchedEffect(videoId) { database.format(videoId).collect { currentFormat = it } }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (info == null) {
            ShimmerHost {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(all = 16.dp),
                ) {
                    TextPlaceholder()
                }
            }
            return@Column
        }

        val unknown = stringResource(Res.string.unknown)
        fun copyItem(label: String, text: String?, icon: DrawableResource?) = Triple(label, text ?: unknown, icon)

        val baseList = listOf(
            copyItem(stringResource(Res.string.song_title), info?.title ?: song?.title, Res.drawable.music_note),
            copyItem(
                stringResource(Res.string.song_artists),
                song?.artists?.joinToString { ArtistNameAliases.resolve(artistNameAliases, it.id, it.name) }
                    ?: info?.author?.let { ArtistNameAliases.resolve(artistNameAliases, null, it) },
                Res.drawable.person,
            ),
            copyItem(stringResource(Res.string.media_id), song?.id ?: info?.videoId, Res.drawable.link),
        )

        val format = currentFormat
        val measuredLufs: Double? = format?.perceptualLoudnessDb
            ?: format?.loudnessDb?.let { it + LoudnessLevel.AGGRESSIVE.targetLufs }
        val extendedList = if (format != null) {
            listOf(
                copyItem(stringResource(Res.string.views), info?.viewCount?.let(::numberFormatter).orEmpty(), Res.drawable.play),
                copyItem(stringResource(Res.string.likes), info?.like?.let(::numberFormatter).orEmpty(), Res.drawable.favorite),
                copyItem(stringResource(Res.string.dislikes), info?.dislike?.let(::numberFormatter).orEmpty(), Res.drawable.favorite_border),
                copyItem("Itag", format.itag.toString(), Res.drawable.key),
                copyItem(stringResource(Res.string.stream_client), currentStreamClient, Res.drawable.play),
                copyItem(stringResource(Res.string.mime_type), format.mimeType, Res.drawable.info),
                copyItem(stringResource(Res.string.codecs), format.codecs, Res.drawable.radio),
                copyItem(stringResource(Res.string.bitrate), "${format.bitrate / 1000} Kbps", Res.drawable.gradient),
                copyItem(stringResource(Res.string.sample_rate), format.sampleRate?.let { "$it Hz" }, Res.drawable.contrast),
                copyItem(
                    stringResource(Res.string.loudness),
                    measuredLufs?.let { String.format(Locale.getDefault(), "%.2f dB", it - targetLufs) },
                    Res.drawable.volume_up,
                ),
                copyItem(stringResource(Res.string.loudness_level), getLoudnessLevelLabel(loudnessLevel), Res.drawable.volume_up),
                copyItem(stringResource(Res.string.volume), "${(playerVolume * 100).toInt()}%", Res.drawable.volume_mute),
                copyItem(stringResource(Res.string.file_size), formatFileSize(format.contentLength), Res.drawable.content_copy),
            )
        } else {
            emptyList()
        }

        @Composable
        fun cards(items: List<Triple<String, String, DrawableResource?>>) = items.map { (label, text, icon) ->
            Material3SettingsItem(
                title = { Text(label) },
                description = { Text(text) },
                icon = icon?.let { painterResource(it) },
                onClick = { DesktopShare.copyToClipboard(text) },
            )
        }

        Material3SettingsGroup(title = stringResource(Res.string.general), items = cards(baseList))
        if (extendedList.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Material3SettingsGroup(title = stringResource(Res.string.information), items = cards(extendedList))
        }
        Spacer(Modifier.height(8.dp))
        val descriptionText = info?.description ?: unknown
        Material3SettingsGroup(
            title = stringResource(Res.string.description),
            items = listOf(
                Material3SettingsItem(
                    title = { Text(stringResource(Res.string.description)) },
                    description = { Text(descriptionText) },
                    onClick = { DesktopShare.copyToClipboard(descriptionText) },
                ),
            ),
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}
