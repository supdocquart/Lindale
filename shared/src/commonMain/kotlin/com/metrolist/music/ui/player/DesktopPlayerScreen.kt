package com.metrolist.music.ui.player

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.extensions.metadata
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.MediaMetadataListItem
import com.metrolist.music.ui.menu.PlayerMenu
import com.metrolist.music.ui.utils.ShowMediaInfo
import com.metrolist.music.ui.utils.resize
import metrolist.shared.generated.resources.Res
import metrolist.shared.generated.resources.expand_more
import metrolist.shared.generated.resources.favorite
import metrolist.shared.generated.resources.favorite_border
import metrolist.shared.generated.resources.lyrics
import metrolist.shared.generated.resources.more_vert
import metrolist.shared.generated.resources.now_playing
import metrolist.shared.generated.resources.queue
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Desktop "now playing" page: cover + track on the left, lyrics/queue on the right,
 * and the same bottom bar as the collapsed state. Replaces the phone portrait player.
 */
@Composable
fun DesktopPlayerScreen(
    state: BottomSheetState,
    positionState: MutableLongState,
    durationState: MutableLongState,
    positionProvider: () -> Long,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle()
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val metadata = mediaMetadata ?: return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    // Building lyrics/queue in the same frame the sheet starts moving made the open animation
    // stutter. Show them once the sheet has finished expanding, with a short fade.
    var panelReady by remember { mutableStateOf(state.isExpanded) }
    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded) panelReady = true
    }
    val panelAlpha by animateFloatAsState(
        targetValue = if (panelReady) 1f else 0f,
        animationSpec = tween(180),
        label = "panelAlpha",
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
        ) {
            IconButton(onClick = { state.collapseSoft() }) {
                Icon(painterResource(Res.drawable.expand_more), contentDescription = null)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(Res.string.now_playing),
                    style = MaterialTheme.typography.labelMedium,
                    color = dim,
                )
                queueTitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = metadata,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = {
                                bottomSheetPageState.show { ShowMediaInfo(metadata.id) }
                            },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
            ) {
                Icon(painterResource(Res.drawable.more_vert), contentDescription = null)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 16.dp),
        ) {
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                val side = min(min(maxWidth, maxHeight - 88.dp), 560.dp).coerceAtLeast(120.dp)
                Column(Modifier.width(side)) {
                    AsyncImage(
                        model = metadata.thumbnailUrl?.resize(1080, 1080),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(side)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = metadata.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = metadata.artists.joinToString { it.name },
                                style = MaterialTheme.typography.bodyLarge,
                                color = dim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val liked = currentSong?.song?.liked == true
                        IconButton(onClick = playerConnection::toggleLike) {
                            Icon(
                                painterResource(if (liked) Res.drawable.favorite else Res.drawable.favorite_border),
                                contentDescription = null,
                                tint = if (liked) MaterialTheme.colorScheme.error else dim,
                            )
                        }
                    }
                }
            }

            Column(Modifier.weight(1f).widthIn(max = 640.dp).fillMaxHeight()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabPill(stringResource(Res.string.lyrics), tab == 0) { tab = 0 }
                    TabPill(stringResource(Res.string.queue), tab == 1) { tab = 1 }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)),
                ) {
                    if (panelReady) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = panelAlpha }) {
                            when (tab) {
                                0 -> InlineLyricsView(
                                    mediaMetadata = metadata,
                                    showLyrics = true,
                                    positionProvider = positionProvider,
                                )
                                else -> DesktopQueueList()
                            }
                        }
                    }
                }
            }
        }

        DesktopPlayerBar(
            positionState = positionState,
            durationState = durationState,
            onExpand = { state.collapseSoft() },
        )
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.onSurface else colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.surface else colors.onSurface,
        )
    }
}

@Composable
private fun DesktopQueueList() {
    val playerConnection = LocalPlayerConnection.current ?: return
    val windows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (currentIndex in windows.indices) listState.scrollToItem(currentIndex)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(windows, key = { _, window -> window.uid.hashCode() }) { index, window ->
            val itemMetadata = window.mediaItem.metadata ?: return@itemsIndexed
            MediaMetadataListItem(
                mediaMetadata = itemMetadata,
                isActive = index == currentIndex,
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (index == currentIndex) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                            playerConnection.player.playWhenReady = true
                        }
                    },
            )
        }
    }
}
