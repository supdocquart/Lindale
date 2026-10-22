package com.metrolist.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * DESKTOP: port of upstream MainActivity's theme seed logic. Returns [selectedColor] unless
 * [dynamic] is on, in which case the seed follows the playing song's cover.
 */
@Composable
fun rememberThemeSeedColor(
    playerConnection: PlayerConnection,
    dynamic: Boolean,
    selectedColor: Color,
): Color {
    var themeColor by remember { mutableStateOf(selectedColor) }
    val cache = remember { mutableMapOf<String, Color>() }

    LaunchedEffect(playerConnection, dynamic, selectedColor) {
        if (!dynamic) {
            themeColor = selectedColor
            return@LaunchedEffect
        }
        playerConnection.mediaMetadata
            .distinctUntilChanged { old, new -> old?.id == new?.id }
            .collectLatest { song ->
                val url = song?.thumbnailUrl
                if (url == null) {
                    themeColor = selectedColor
                    return@collectLatest
                }
                cache[url]?.let {
                    themeColor = it
                    return@collectLatest
                }
                val extracted = withContext(Dispatchers.IO) {
                    runCatching {
                        val request = ImageRequest.Builder(PlatformContext.INSTANCE)
                            .data(url)
                            .size(128, 128)
                            .build()
                        val image = SingletonImageLoader.get(PlatformContext.INSTANCE).execute(request).image
                        image?.toBitmap()?.let { withContext(Dispatchers.Default) { extractThemeColor(it) } }
                    }.getOrNull()
                } ?: selectedColor
                cache[url] = extracted
                themeColor = extracted
            }
    }
    return themeColor
}
