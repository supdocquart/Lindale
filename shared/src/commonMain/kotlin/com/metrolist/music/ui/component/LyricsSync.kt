// DESKTOP: "Sync lyrics" mode. While it is on, clicking a lyric line means "this line is being sung right
// now": the per-song offset becomes line time minus playback position, instead of seeking to the line.
// A banner offers ±0.1 s fine tuning, reset and done. The offset is stored in SongEntity.lyricsOffset
// (the column upstream's offset dialog already uses), so no schema change.
package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.SongEntity
import kotlinx.coroutines.flow.MutableStateFlow
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

object LyricsSync {
    val active = MutableStateFlow(false)

    private const val MAX_OFFSET_MS = 120_000

    fun setOffset(database: MusicDatabase, song: SongEntity?, offsetMs: Int) {
        song ?: return
        val clamped = offsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        database.query { upsert(song.copy(lyricsOffset = clamped)) }
    }

    /** The clicked line (at [lineTimeMs] in the lyrics) is the one being sung at [positionMs]. */
    fun syncLineToNow(database: MusicDatabase, song: SongEntity?, lineTimeMs: Long, positionMs: Long) {
        setOffset(database, song, (lineTimeMs - positionMs).toInt())
    }
}

@Composable
fun LyricsSyncBanner(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val song = currentSong?.song
    val offset = song?.lyricsOffset ?: 0

    // A different song needs its own sync; leave the mode when the track changes.
    var startedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentSong?.id) {
        val id = currentSong?.id ?: return@LaunchedEffect
        if (startedFor == null) startedFor = id else if (id != startedFor) LyricsSync.active.value = false
    }

    Surface(
        modifier = modifier.padding(12.dp).widthIn(max = 560.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(Res.string.lyrics_sync_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { LyricsSync.setOffset(database, song, offset - 100) }) { Text("−0.1 s") }
                Text(
                    text = (if (offset >= 0) "+" else "−") + "%.1f s".format(kotlin.math.abs(offset) / 1000.0),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                TextButton(onClick = { LyricsSync.setOffset(database, song, offset + 100) }) { Text("+0.1 s") }
                TextButton(onClick = { LyricsSync.setOffset(database, song, 0) }) {
                    Text(stringResource(Res.string.lyrics_sync_reset))
                }
                Button(onClick = { LyricsSync.active.value = false }) {
                    Text(stringResource(Res.string.lyrics_sync_done))
                }
            }
        }
    }
}
