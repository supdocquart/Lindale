package com.metrolist.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.playback.Player
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.makeTimeString
import metrolist.shared.generated.resources.Res
import metrolist.shared.generated.resources.favorite
import metrolist.shared.generated.resources.favorite_border
import metrolist.shared.generated.resources.fullscreen
import metrolist.shared.generated.resources.pause
import metrolist.shared.generated.resources.play
import metrolist.shared.generated.resources.repeat
import metrolist.shared.generated.resources.repeat_on
import metrolist.shared.generated.resources.repeat_one_on
import metrolist.shared.generated.resources.shuffle
import metrolist.shared.generated.resources.shuffle_on
import metrolist.shared.generated.resources.skip_next
import metrolist.shared.generated.resources.skip_previous
import metrolist.shared.generated.resources.volume_down
import metrolist.shared.generated.resources.volume_off
import metrolist.shared.generated.resources.volume_up
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

val DesktopPlayerBarHeight = 80.dp

/** Desktop bottom bar: track info, transport + seek bar, volume. Replaces the phone mini player. */
@Composable
fun DesktopPlayerBar(
    positionState: MutableLongState,
    durationState: MutableLongState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val shuffleOn by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val isMuted by playerConnection.isMuted.collectAsStateWithLifecycle()
    val volume by playerConnection.service.playerVolume.collectAsStateWithLifecycle()
    val metadata = mediaMetadata ?: return

    val accent = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier
            .fillMaxWidth()
            .height(DesktopPlayerBarHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        ) {
            // Warm the disk cache with the large cover the now-playing page uses, so opening it
            // doesn't wait on the network mid-animation.
            val bigCover = metadata.thumbnailUrl?.resize(1080, 1080)
            androidx.compose.runtime.LaunchedEffect(bigCover) {
                if (bigCover != null) {
                    coil3.SingletonImageLoader.get(coil3.PlatformContext.INSTANCE).enqueue(
                        coil3.request.ImageRequest.Builder(coil3.PlatformContext.INSTANCE)
                            .data(bigCover)
                            .size(64, 64) // only the downloaded bytes matter; keep the decode cheap
                            .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
                            .build(),
                    )
                }
            }
            // Left: track
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(0.3f),
            ) {
                AsyncImage(
                    model = metadata.thumbnailUrl?.resize(200, 200),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onExpand),
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f, fill = false).clickable(onClick = onExpand),
                ) {
                    Text(
                        text = metadata.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metadata.artists.joinToString { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val liked = currentSong?.song?.liked == true
                BarIcon(
                    icon = if (liked) Res.drawable.favorite else Res.drawable.favorite_border,
                    tint = if (liked) accent else dim,
                    onClick = playerConnection::toggleLike,
                )
            }

            // Center: transport + seek
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(0.4f).widthIn(max = 720.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BarIcon(
                        icon = if (shuffleOn) Res.drawable.shuffle_on else Res.drawable.shuffle,
                        tint = if (shuffleOn) accent else dim,
                        onClick = { playerConnection.player.shuffleModeEnabled = !shuffleOn },
                    )
                    BarIcon(Res.drawable.skip_previous, enabled = canSkipPrevious, onClick = playerConnection::seekToPrevious)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface)
                            .clickable(onClick = playerConnection::togglePlayPause),
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) Res.drawable.pause else Res.drawable.play),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    BarIcon(Res.drawable.skip_next, enabled = canSkipNext, onClick = playerConnection::seekToNext)
                    BarIcon(
                        icon = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Res.drawable.repeat_one_on
                            Player.REPEAT_MODE_ALL -> Res.drawable.repeat_on
                            else -> Res.drawable.repeat
                        },
                        tint = if (repeatMode == Player.REPEAT_MODE_OFF) dim else accent,
                        onClick = playerConnection::toggleRepeatMode,
                    )
                }
                SeekRow(positionState, durationState, onSeek = playerConnection::seekTo)
            }

            // Right: open player + volume
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(0.3f),
            ) {
                BarIcon(Res.drawable.fullscreen, onClick = onExpand)
                Spacer(Modifier.width(4.dp))
                BarIcon(
                    icon = when {
                        isMuted || volume <= 0f -> Res.drawable.volume_off
                        volume < 0.5f -> Res.drawable.volume_down
                        else -> Res.drawable.volume_up
                    },
                    onClick = playerConnection::toggleMute,
                )
                ThinSlider(
                    value = if (isMuted) 0f else volume,
                    onValueChange = {
                        if (isMuted && it > 0f) playerConnection.setMuted(false)
                        playerConnection.service.playerVolume.value = it
                    },
                    modifier = Modifier.width(110.dp),
                )
            }
        }
    }
}

@Composable
private fun SeekRow(
    positionState: MutableLongState,
    durationState: MutableLongState,
    onSeek: (Long) -> Unit,
) {
    val duration = durationState.longValue.coerceAtLeast(0L)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shownPosition = dragFraction?.let { (it * duration).toLong() } ?: positionState.longValue
    val fraction = if (duration > 0) (shownPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TimeText(makeTimeString(shownPosition), Alignment.CenterEnd)
        ThinSlider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * duration).toLong()) }
                dragFraction = null
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        TimeText(makeTimeString(duration), Alignment.CenterStart)
    }
}

@Composable
private fun TimeText(text: String, alignment: Alignment) {
    Box(Modifier.width(44.dp), contentAlignment = alignment) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BarIcon(
    icon: DrawableResource,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 20.dp,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(size),
        )
    }
}

/** Slim desktop slider: thin track, thumb shown on hover or drag; click or drag to set. */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val active = if (hovered || dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val showThumb = hovered || dragging
    Canvas(
        modifier
            .height(16.dp)
            .hoverable(hoverSource)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                    onValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        dragging = false
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        dragging = false
                        onValueChangeFinished()
                    },
                ) { change, _ ->
                    onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        widthPx = size.width
        val track = 4.dp.toPx()
        val y = (size.height - track) / 2f
        val radius = CornerRadius(track / 2f)
        drawRoundRect(inactive, Offset(0f, y), Size(size.width, track), radius)
        drawRoundRect(active, Offset(0f, y), Size(size.width * value.coerceIn(0f, 1f), track), radius)
        if (showThumb) {
            drawCircle(Color.White, 6.dp.toPx(), Offset(size.width * value.coerceIn(0f, 1f), size.height / 2f))
        }
    }
}
