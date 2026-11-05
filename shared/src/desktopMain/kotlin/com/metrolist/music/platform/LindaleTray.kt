package com.metrolist.music.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import com.metrolist.music.playback.PlayerConnection
import metrolist.shared.generated.resources.Res
import metrolist.shared.generated.resources.tray_next
import metrolist.shared.generated.resources.tray_play_pause
import metrolist.shared.generated.resources.tray_previous
import metrolist.shared.generated.resources.tray_quit
import metrolist.shared.generated.resources.tray_show
import org.jetbrains.compose.resources.stringResource

/**
 * DESKTOP: icon next to the clock with the basic player controls. Clicking it (or "Show") brings the
 * window back after "close to tray" hid it. Not shown when the desktop has no system tray.
 */
@Composable
fun ApplicationScope.LindaleTray(
    player: PlayerConnection,
    nowPlaying: String?,
    onShow: () -> Unit,
    onQuit: () -> Unit,
) {
    if (!remember { runCatching { java.awt.SystemTray.isSupported() }.getOrDefault(false) }) return
    val icon = remember { BitmapPainter(useResource("lindale.png") { loadImageBitmap(it) }) }
    Tray(
        icon = icon,
        tooltip = listOfNotNull(AppBrand.DISPLAY_NAME, nowPlaying).joinToString(" - "),
        onAction = onShow,
        menu = {
            Item(stringResource(Res.string.tray_play_pause), onClick = player::togglePlayPause)
            Item(stringResource(Res.string.tray_next), onClick = player::seekToNext)
            Item(stringResource(Res.string.tray_previous), onClick = player::seekToPrevious)
            Separator()
            Item(stringResource(Res.string.tray_show), onClick = onShow)
            Item(stringResource(Res.string.tray_quit), onClick = onQuit)
        },
    )
}
