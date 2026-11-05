package com.metrolist.music.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.delay
import metrolist.shared.generated.resources.Res
import metrolist.shared.generated.resources.open_in_full
import metrolist.shared.generated.resources.pause
import metrolist.shared.generated.resources.play
import metrolist.shared.generated.resources.skip_next
import metrolist.shared.generated.resources.skip_previous
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * DESKTOP: small always-on-top player window (cover, title, previous / play / next, progress).
 * Drag it anywhere; the expand button (or closing it) brings the main window back.
 */
@Composable
fun MiniPlayerWindow(player: PlayerConnection, onRestore: () -> Unit) {
    val state = rememberWindowState(
        size = DpSize(380.dp, 96.dp),
        position = WindowPosition(Alignment.BottomEnd),
    )
    val icon = remember { BitmapPainter(useResource("lindale.png") { loadImageBitmap(it) }) }
    Window(
        onCloseRequest = onRestore,
        state = state,
        title = AppBrand.DISPLAY_NAME,
        icon = icon,
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            WindowDraggableArea {
                MiniPlayerContent(player, onRestore)
            }
        }
    }
}

@Composable
private fun MiniPlayerContent(player: PlayerConnection, onRestore: () -> Unit) {
    val metadata by player.mediaMetadata.collectAsState()
    val isPlaying by player.isEffectivelyPlaying.collectAsState()
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            val duration = player.player.duration
            progress = if (duration > 0) (player.player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
        ) {
            AsyncImage(
                model = metadata?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = metadata?.title.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metadata?.artists?.joinToString { it.name }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MiniButton(Res.drawable.skip_previous, player::seekToPrevious)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
                    .clickable(onClick = player::togglePlayPause),
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) Res.drawable.pause else Res.drawable.play),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(22.dp),
                )
            }
            MiniButton(Res.drawable.skip_next, player::seekToNext)
            MiniButton(Res.drawable.open_in_full, onRestore, size = 18)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun MiniButton(icon: DrawableResource, onClick: () -> Unit, size: Int = 22) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size.dp),
        )
    }
}
