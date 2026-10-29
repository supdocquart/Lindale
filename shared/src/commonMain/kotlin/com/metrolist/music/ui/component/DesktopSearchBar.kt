package com.metrolist.music.ui.component

import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.PlayerConnection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.utils.YouTubeUrlParser
import com.metrolist.music.constants.PauseSearchHistoryKey
import com.metrolist.music.db.entities.SearchHistory
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.screens.search.OnlineSearchScreen
import com.metrolist.music.utils.SearchRoutes
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import metrolist.shared.generated.resources.Res
import metrolist.shared.generated.resources.close
import metrolist.shared.generated.resources.search
import metrolist.shared.generated.resources.search_in_app
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Lets the window route Ctrl+K / Ctrl+F to the search field from anywhere. */
object GlobalSearch {
    @Volatile
    var focusHandler: (() -> Unit)? = null

    fun focus() {
        focusHandler?.invoke()
    }
}

/**
 * Same behaviour as the search screen's submit: YouTube links open directly,
 * anything else goes to the results page and into search history.
 */
@Composable
fun rememberSearchSubmit(
    navController: NavController,
    database: MusicDatabase,
    playerConnection: PlayerConnection?,
): (String) -> Unit {
    val scope = rememberCoroutineScope()
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    return submit@{ raw ->
        val searchQuery = raw.trim()
        if (searchQuery.isEmpty()) return@submit
        when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
            is YouTubeUrlParser.ParsedUrl.Video -> playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = parsedUrl.id)))
            is YouTubeUrlParser.ParsedUrl.Playlist -> navController.navigate("online_playlist/${parsedUrl.id}")
            is YouTubeUrlParser.ParsedUrl.Album -> navController.navigate("album/MPREb_${parsedUrl.id}")
            is YouTubeUrlParser.ParsedUrl.Artist -> navController.navigate("artist/${parsedUrl.id}")
            null -> navController.navigate(SearchRoutes.resultRoute(searchQuery))
        }
        if (!pauseSearchHistory) {
            scope.launch(Dispatchers.IO) {
                database.query { insert(SearchHistory(query = searchQuery)) }
            }
        }
    }
}

@Composable
fun DesktopSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: (String) -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.height(44.dp).widthIn(max = 640.dp).fillMaxWidth()
            // A click on a field that already has focus fires no focus change, so reopen the panel here.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    onFocused()
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 4.dp)) {
            Icon(
                painter = painterResource(Res.drawable.search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.search_in_app),
                        style = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit(value.text) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (it.isFocused) onFocused() }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                onSubmit(value.text)
                                true
                            } else {
                                false
                            }
                        },
                )
            }
            if (value.text.isNotEmpty()) {
                IconButton(onClick = { onValueChange(TextFieldValue("")) }) {
                    Icon(
                        painter = painterResource(Res.drawable.close),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Search history and suggestions shown under the field while it is open. */
@Composable
fun DesktopSearchPanel(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: (String) -> Unit,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(max = 520.dp),
    ) {
        OnlineSearchScreen(
            query = value.text,
            onQueryChange = onValueChange,
            onSearch = onSubmit,
            onDismiss = {},
            pureBlack = pureBlack,
        )
    }
}
