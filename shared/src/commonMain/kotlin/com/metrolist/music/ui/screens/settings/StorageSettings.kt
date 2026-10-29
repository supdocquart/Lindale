/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings
import com.metrolist.music.platform.LocalAppContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.constants.MaxImageCacheSizeKey
import com.metrolist.music.extensions.tryOrNull
import com.metrolist.music.ui.component.ActionPromptDialog
import com.metrolist.music.ui.component.DownloadCenter
import com.metrolist.music.playback.Download
import androidx.compose.runtime.collectAsState
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.formatFileSize
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class, DelicateCoilApi::class)
@Composable
fun StorageSettings(
    navController: NavController
) {
    val context = LocalAppContext.current
    val database = LocalDatabase.current
    val imageDiskCache = tryOrNull { coil3.SingletonImageLoader.get(coil3.PlatformContext.INSTANCE).diskCache }

    val coroutineScope = rememberCoroutineScope()
    val imageCacheString = stringResource(Res.string.image_cache).lowercase()
    val (maxImageCacheSize, onMaxImageCacheSizeChange) = rememberPreference(
        key = MaxImageCacheSizeKey,
        defaultValue = 512
    )

    var clearDownloads by remember { mutableStateOf(false) }
    var clearImageCacheDialog by remember { mutableStateOf(false) }

    // State for the confirmation dialog
    var showCacheWarningDialog by remember { mutableStateOf(false) }
    var cacheType by remember { mutableStateOf("") }
    var cacheUsage by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var onConfirmAction by remember { mutableStateOf<() -> Unit>({}) }

    var imageCacheSize by remember {
        androidx.compose.runtime.mutableLongStateOf(tryOrNull { imageDiskCache?.size } ?: 0L)
    }
    // DESKTOP: songs stream without a disk cache, so this screen only covers downloads and images.
    val downloads by DownloadCenter.downloads.collectAsState()
    val downloadBytes = remember(downloads) { DownloadCenter.totalBytes() }
    val imageCacheProgress by animateFloatAsState(
        targetValue =
            if (maxImageCacheSize > 0) {
                (imageCacheSize.toFloat() / (maxImageCacheSize * 1024 * 1024L)).coerceIn(0f, 1f)
            } else {
                0f
            },
        label = "imageCacheProgress",
    )
    LaunchedEffect(maxImageCacheSize) {
        SingletonImageLoader.reset()
        if (maxImageCacheSize == 0) {
            coroutineScope.launch(Dispatchers.IO) {
                imageDiskCache?.clear()
            }
        }
    }
    LaunchedEffect(imageDiskCache) {
        if (imageDiskCache == null) return@LaunchedEffect
        while (isActive) {
            delay(500)
            imageCacheSize = tryOrNull { imageDiskCache.size } ?: 0L
        }
    }
    if (clearDownloads) {
        ActionPromptDialog(
            title = stringResource(Res.string.clear_all_downloads),
            onDismiss = { clearDownloads = false },
            onConfirm = {
                DownloadCenter.removeAll()
                clearDownloads = false
            },
            onCancel = { clearDownloads = false },
            content = {
                Text(text = stringResource(Res.string.clear_downloads_dialog))
            },
        )
    }
    if (clearImageCacheDialog) {
        ActionPromptDialog(
            title = stringResource(Res.string.clear_image_cache),
            onDismiss = { clearImageCacheDialog = false },
            onConfirm = {
                coroutineScope.launch(Dispatchers.IO) {
                    val urlsToPreserve = mutableSetOf<String>()
                    val downloadedSongs =
                        try {
                            database.downloadedSongsByNameAsc().first()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    downloadedSongs.forEach { song ->
                        song.song.thumbnailUrl?.let { urlsToPreserve.add(it.encodeUtf8().sha256().hex()) }
                        song.album?.thumbnailUrl?.let { urlsToPreserve.add(it.encodeUtf8().sha256().hex()) }
                    }
                    imageDiskCache?.let { diskCache ->
                        val directory = File(diskCache.directory.toString())
                        if (directory.exists() && directory.isDirectory) {
                            directory.listFiles()?.forEach { file: File ->
                                if (file.isFile && !file.name.startsWith("journal")) {
                                    val isPreserved = urlsToPreserve.any { hash -> file.name.startsWith(hash) }
                                    if (!isPreserved) {
                                        file.delete()
                                    }
                                }
                            }
                        }
                        diskCache.clear()
                    }
                }
                clearImageCacheDialog = false
            },
            onCancel = { clearImageCacheDialog = false },
            content = {
                Text(text = stringResource(Res.string.clear_image_cache_dialog))
            },
        )
    }

    // Confirmation Dialog
    if (showCacheWarningDialog) {
        AlertDialog(
            onDismissRequest = { showCacheWarningDialog = false },
            title = { Text(stringResource(Res.string.cache_size_warning_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.cache_size_warning_message,
                        formatFileSize(cacheUsage),
                        cacheType,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirmAction()
                        showCacheWarningDialog = false
                    },
                ) {
                    Text(
                        stringResource(Res.string.cache_size_warning_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCacheWarningDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top,
                ),
            ),
        )
        Material3SettingsGroup(
            title = stringResource(Res.string.storage),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(Res.drawable.storage),
                        title = { Text(stringResource(Res.string.downloaded_songs)) },
                        description = {
                            Text(text = "${downloads.count { it.value.state == Download.STATE_COMPLETED }} · ${formatFileSize(downloadBytes)}")
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(Res.drawable.download),
                        title = { Text(stringResource(Res.string.download_folder)) },
                        description = { Text(DownloadCenter.directory.absolutePath) },
                        onClick = {
                            runCatching { java.awt.Desktop.getDesktop().open(DownloadCenter.directory) }
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(Res.drawable.clear_all),
                        title = { Text(stringResource(Res.string.clear_all_downloads)) },
                        onClick = {
                            clearDownloads = true
                        },
                    ),
                ),
        )

        Material3SettingsGroup(
            title = stringResource(Res.string.image_cache),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(Res.drawable.manage_search),
                        title = { Text(stringResource(Res.string.max_image_cache_size)) },
                        description = {
                            val imageCacheValues =
                                remember { listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192) }
                            Column {
                                Text(
                                    text =
                                        when (maxImageCacheSize) {
                                            0 -> stringResource(Res.string.disable)
                                            else -> formatFileSize(maxImageCacheSize * 1024 * 1024L)
                                        },
                                )
                                Slider(
                                    value = imageCacheValues.indexOf(maxImageCacheSize).toFloat(),
                                    onValueChange = {
                                        val newValue = imageCacheValues[it.roundToInt()]
                                        val newLimitInBytes = newValue * 1024 * 1024L

                                        if (newLimitInBytes < imageCacheSize) {
                                            cacheUsage = imageCacheSize
                                            cacheType = imageCacheString
                                            onConfirmAction = { onMaxImageCacheSizeChange(newValue) }
                                            showCacheWarningDialog = true
                                        } else {
                                            onMaxImageCacheSizeChange(newValue)
                                        }
                                    },
                                    steps = imageCacheValues.size - 2,
                                    valueRange = 0f..(imageCacheValues.size - 1).toFloat(),
                                )
                                LinearProgressIndicator(
                                    progress = { imageCacheProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    strokeCap = StrokeCap.Round,
                                )
                                Spacer(modifier = Modifier.padding(2.dp))
                                Text(
                                    text = "${formatFileSize(imageCacheSize)} / ${
                                        formatFileSize(maxImageCacheSize * 1024 * 1024L,
                                        )
                                    }",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(Res.drawable.clear_all),
                        title = { Text(stringResource(Res.string.clear_image_cache)) },
                        onClick = {
                            clearImageCacheDialog = true
                        },
                    ),
                ),
        )
    }

    TopAppBar(
        title = { Text(stringResource(Res.string.storage)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(Res.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
