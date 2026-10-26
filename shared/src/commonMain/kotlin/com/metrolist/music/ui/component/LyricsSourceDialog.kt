// DESKTOP: "Change lyrics source". Lists every enabled lyrics provider, searches all of them at once and
// shows per provider whether it found synced lyrics, plain lyrics or nothing, with a short preview.
package com.metrolist.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.lyrics.LyricsSourceSearch
import com.metrolist.music.lyrics.LyricsSourceSearch.Status
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.platform.LocalAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private class SourceRow(val name: String, val status: MutableState<Status>)

@Composable
fun LyricsSourceDialog(
    mediaMetadata: MediaMetadata,
    currentProvider: String?,
    onDismiss: () -> Unit,
    onPick: (providerName: String, lyrics: String) -> Unit,
) {
    val context = LocalAppContext.current
    val rows = remember(mediaMetadata.id) { mutableStateListOf<SourceRow>() }

    LaunchedEffect(mediaMetadata.id) {
        val providers = LyricsSourceSearch.enabledProviders(context)
        val created = providers.map { it to SourceRow(it.name, mutableStateOf(Status.Searching)) }
        rows.clear()
        rows.addAll(created.map { it.second })
        coroutineScope {
            created.forEach { (provider, row) ->
                launch(Dispatchers.IO) { row.status.value = LyricsSourceSearch.search(context, provider, mediaMetadata) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.lyrics_source_title)) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(rows, key = { it.name }) { row ->
                    val status = row.status.value
                    val found = status as? Status.Found
                    val isCurrent = row.name == currentProvider
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (status is Status.NotFound) 0.5f else 1f)
                            .clickable(enabled = found != null) { found?.let { onPick(row.name, it.lyrics) } },
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = row.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                )
                                when (status) {
                                    Status.Searching -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    is Status.Found -> Text(
                                        text = stringResource(
                                            if (status.synced) Res.string.lyrics_source_synced else Res.string.lyrics_source_plain,
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (status.synced) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Status.NotFound -> Text(
                                        text = stringResource(Res.string.lyrics_source_none),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (found != null && found.preview.isNotBlank()) {
                                Text(
                                    text = found.preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isCurrent) {
                                Text(
                                    text = stringResource(Res.string.lyrics_source_current),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
