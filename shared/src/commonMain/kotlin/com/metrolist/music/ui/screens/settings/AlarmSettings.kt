package com.metrolist.music.ui.screens.settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import com.metrolist.music.utils.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import com.metrolist.music.platform.toUri
import com.metrolist.music.LocalDatabase
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.playback.alarm.MusicAlarmEntry
import com.metrolist.music.playback.alarm.MusicAlarmScheduler
import com.metrolist.music.playback.alarm.MusicAlarmStore
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlarmSettingsSection(showTitle: Boolean = true) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val playlists by database.playlistsByNameAsc().collectAsStateWithLifecycle(initialValue = emptyList())
    val persistMutex = remember { Mutex() }
    val selectPlaylistText = stringResource(Res.string.alarm_select_playlist)
    val randomEnabledText = stringResource(Res.string.alarm_random_enabled)
    val randomDisabledText = stringResource(Res.string.alarm_random_disabled)
    val notScheduledText = stringResource(Res.string.alarm_not_scheduled)

    var alarms by remember { mutableStateOf(emptyList<MusicAlarmEntry>()) }
    var showEditor by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<MusicAlarmEntry?>(null) }

    val systemItems = emptyList<Material3SettingsItem>()

    suspend fun loadAlarms(): List<MusicAlarmEntry> {
        return withContext(Dispatchers.IO) {
            MusicAlarmStore.load(context)
        }.sortedBy { it.hour * 60 + it.minute }
    }

    fun persistAndSchedule(transform: (List<MusicAlarmEntry>) -> List<MusicAlarmEntry>) {
        scope.launch {
            persistMutex.withLock {
                val latest = loadAlarms()
                val newList = transform(latest)
                withContext(Dispatchers.IO) {
                    MusicAlarmScheduler.scheduleAll(context, newList)
                }
                alarms = loadAlarms()
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        alarms = loadAlarms()
    }

    if (showEditor) {
        AlarmEditorDialog(
            existing = editorTarget,
            allAlarms = alarms,
            playlists = playlists,
            onDismiss = {
                showEditor = false
                editorTarget = null
            },
            onSave = { updated ->
                persistAndSchedule { current ->
                    current.filterNot { it.id == updated.id } + updated
                }
                showEditor = false
                editorTarget = null
            }
        )
    }

    Material3SettingsGroup(
        title = if (showTitle) stringResource(Res.string.alarm) else null,
        items = buildList {
            add(
                Material3SettingsItem(
                    icon = painterResource(Res.drawable.add_circle),
                    title = { Text(stringResource(Res.string.alarm_add)) },
                    onClick = {
                        editorTarget = null
                        showEditor = true
                    }
                )
            )

            if (alarms.isEmpty()) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(Res.drawable.bedtime),
                        title = { Text(stringResource(Res.string.alarm_empty)) }
                    )
                )
            } else {
                addAll(
                    alarms.map { alarm ->
                        val playlistTitle =
                            playlists.firstOrNull { it.id == alarm.playlistId }?.title
                                ?: selectPlaylistText
                        val triggerText =
                            if (alarm.nextTriggerAt > 0L) {
                                DateTimeFormatter.ofPattern("EEE, HH:mm", locale)
                                    .format(
                                        Instant.ofEpochMilli(alarm.nextTriggerAt)
                                            .atZone(ZoneId.systemDefault())
                                    )
                            } else {
                                notScheduledText
                            }
                        val description = buildString {
                            append(playlistTitle)
                            append(" • ")
                            append(if (alarm.randomSong) randomEnabledText else randomDisabledText)
                            append("\n")
                            append(stringResource(Res.string.alarm_next_prefix, triggerText))
                        }

                        Material3SettingsItem(
                            icon = painterResource(Res.drawable.bedtime),
                            title = {
                                Text(
                                    String.format(locale, "%02d:%02d", alarm.hour, alarm.minute) +
                                        if (alarm.enabled) {
                                            ""
                                        } else {
                                            " (${stringResource(Res.string.alarm_disabled)})"
                                        }
                                )
                            },
                            description = { Text(description) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AlarmSwitch(
                                        checked = alarm.enabled,
                                        onCheckedChange = { enabled ->
                                            persistAndSchedule { current ->
                                                current.map {
                                                    if (it.id == alarm.id) it.copy(enabled = enabled) else it
                                                }
                                            }
                                        }
                                    )
                                    IconButton(
                                        onClick = {
                                            persistAndSchedule { current ->
                                                current.filterNot { it.id == alarm.id }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.delete),
                                            contentDescription = stringResource(Res.string.alarm_delete)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                editorTarget = alarm
                                showEditor = true
                            }
                        )
                    }
                )
            }

            addAll(systemItems)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    DefaultDialog(
        title = { Text(title) },
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
            TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                Text(stringResource(Res.string.ok))
            }
        }
    ) {
        TimePicker(state = timePickerState)
    }
}

@Composable
private fun AlarmSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        thumbContent = {
            Icon(
                painter = painterResource(if (checked) Res.drawable.check else Res.drawable.close),
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlarmEditorDialog(
    existing: MusicAlarmEntry?,
    allAlarms: List<MusicAlarmEntry>,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSave: (MusicAlarmEntry) -> Unit
) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val noPlaylistsText = stringResource(Res.string.alarm_no_playlists)
    val selectPlaylistText = stringResource(Res.string.alarm_select_playlist)

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var hour by remember { mutableIntStateOf(existing?.hour ?: 7) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: 0) }
    var playlistId by remember { mutableStateOf(existing?.playlistId.orEmpty()) }
    var randomSong by remember { mutableStateOf(existing?.randomSong ?: false) }

    val hasSameTimeAlarm = remember(hour, minute, existing, allAlarms) {
        allAlarms.any { it.id != existing?.id && it.hour == hour && it.minute == minute }
    }
    val selectedPlaylist = playlists.firstOrNull { it.id == playlistId }
    val hasValidPlaylist = selectedPlaylist != null

    if (showTimePickerDialog) {
        AlarmTimePickerDialog(
            title = stringResource(Res.string.alarm_time),
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showTimePickerDialog = false },
            onConfirm = { selectedHour, selectedMinute ->
                hour = selectedHour
                minute = selectedMinute
                showTimePickerDialog = false
            }
        )
    }

    if (showPlaylistDialog) {
        DefaultDialog(
            onDismiss = { showPlaylistDialog = false },
            title = { Text(stringResource(Res.string.alarm_playlist)) },
            buttons = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text(stringResource(Res.string.ok))
                }
            }
        ) {
            if (playlists.isEmpty()) {
                Text(noPlaylistsText)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(items = playlists, key = { it.id }) { playlist ->
                        val selected = playlist.id == playlistId
                        Card(
                            onClick = {
                                playlistId = playlist.id
                                showPlaylistDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.title,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = pluralStringResource(
                                            Res.plurals.alarm_playlist_song_count,
                                            playlist.songCount,
                                            playlist.songCount
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        painter = painterResource(Res.drawable.check),
                                        contentDescription = stringResource(Res.string.alarm_selected),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                if (existing == null) {
                    stringResource(Res.string.alarm_new)
                } else {
                    stringResource(Res.string.alarm_edit)
                }
            )
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
            TextButton(
                enabled = !hasSameTimeAlarm && hasValidPlaylist,
                onClick = {
                    if (hasSameTimeAlarm) {
                        return@TextButton
                    }
                    if (!hasValidPlaylist) {
                        Toast.makeText(context, selectPlaylistText, Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    onSave(
                        (existing ?: MusicAlarmStore.createEmpty()).copy(
                            enabled = enabled,
                            hour = hour,
                            minute = minute,
                            playlistId = playlistId,
                            randomSong = randomSong
                        )
                    )
                }
            ) {
                Text(stringResource(Res.string.alarm_save))
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.alarm_enabled),
                    modifier = Modifier.weight(1f)
                )
                AlarmSwitch(checked = enabled, onCheckedChange = { enabled = it })
            }

            FilledTonalButton(
                onClick = { showTimePickerDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        Res.string.alarm_time_picker_value,
                        String.format(locale, "%02d:%02d", hour, minute)
                    )
                )
            }

            HorizontalDivider()

            OutlinedButton(
                onClick = {
                    if (playlists.isEmpty()) {
                        Toast.makeText(context, noPlaylistsText, Toast.LENGTH_SHORT).show()
                    } else {
                        showPlaylistDialog = true
                    }
                },
                enabled = playlists.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = selectedPlaylist?.title ?: selectPlaylistText)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.alarm_random_song),
                    modifier = Modifier.weight(1f)
                )
                AlarmSwitch(checked = randomSong, onCheckedChange = { randomSong = it })
            }

            if (hasSameTimeAlarm) {
                Text(
                    text = stringResource(Res.string.alarm_duplicate_time_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}