import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.KeyEvent
import com.metrolist.music.ui.component.GlobalSearch
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.metrolist.music.platform.trimDesktopMemory
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.metrolist.music.ui.utils.BackDispatcher
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.metrolist.music.MetrolistApp
import com.metrolist.music.db.createDesktopDatabase
import com.metrolist.music.playback.DesktopPlayerConnection
import com.metrolist.music.platform.initDesktopPlatform

fun main() {
    // Lets a launcher pick Skia's backend (e.g. OPENGL, SOFTWARE) for memory comparisons.
    System.getenv("METROLIST_RENDER_API")?.takeIf { it.isNotBlank() }?.let { System.setProperty("skiko.renderApi", it) }
    DebugLog.install()
    runApp()
}

private fun runApp() = application {
    initDesktopPlatform()

    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    val database = remember { createDesktopDatabase() }
    val playerConnection = remember(database) { DesktopPlayerConnection(database) }
    remember(playerConnection) {
        com.metrolist.music.discord.DesktopDiscordPresence(playerConnection, database).also { it.start() }
    }
    remember(playerConnection) {
        com.metrolist.music.playback.DesktopScrobbler(playerConnection).also { it.start() }
    }

    Window(
        onCloseRequest = {
            playerConnection.release()
            exitApplication()
        },
        title = "Metrolist",
        state = windowState,
        onKeyEvent = { event ->
            when {
                event.type != KeyEventType.KeyDown -> false
                event.key == Key.Escape -> BackDispatcher.dispatch()
                event.isCtrlPressed && (event.key == Key.K || event.key == Key.F) -> {
                    GlobalSearch.focus()
                    true
                }
                else -> handlePlayerShortcut(event, playerConnection)
            }
        },
    ) {
        LaunchedEffect(windowState.isMinimized) {
            if (windowState.isMinimized) {
                delay(5_000)
                trimDesktopMemory()
            }
        }
        DisposableEffect(Unit) {
            // Mouse "back" side button (XButton1) arrives as AWT button 4.
            val listener = java.awt.event.AWTEventListener { ev ->
                if (ev is java.awt.event.MouseEvent && ev.id == java.awt.event.MouseEvent.MOUSE_PRESSED && ev.button == 4) {
                    BackDispatcher.dispatch()
                }
            }
            java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(listener, java.awt.AWTEvent.MOUSE_EVENT_MASK)
            onDispose { java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
        }
        MetrolistApp(
            database = database,
            playerConnection = playerConnection,
        )
    }
}

/**
 * Spotify-style shortcuts. The window only sees keys nothing focused consumed, so typing a space
 * in the search box never toggles playback.
 */
private fun handlePlayerShortcut(event: KeyEvent, player: DesktopPlayerConnection): Boolean {
    if (event.isAltPressed) return false
    val ctrl = event.isCtrlPressed
    val shift = event.isShiftPressed
    fun changeVolume(delta: Float) {
        val volume = player.service.playerVolume
        if (player.isMuted.value) player.setMuted(false)
        volume.value = (volume.value + delta).coerceIn(0f, 1f)
    }
    fun seekBy(deltaMs: Long) {
        val duration = player.player.duration.takeIf { it > 0 } ?: return
        player.seekTo((player.player.currentPosition + deltaMs).coerceIn(0L, duration))
    }
    when {
        event.key == Key.MediaPlayPause -> player.togglePlayPause()
        event.key == Key.MediaNext -> player.seekToNext()
        event.key == Key.MediaPrevious -> player.seekToPrevious()
        event.key == Key.Spacebar && !ctrl && !shift -> player.togglePlayPause()
        ctrl && !shift && event.key == Key.DirectionRight -> player.seekToNext()
        ctrl && !shift && event.key == Key.DirectionLeft -> player.seekToPrevious()
        ctrl && !shift && event.key == Key.DirectionUp -> changeVolume(0.1f)
        ctrl && !shift && event.key == Key.DirectionDown -> changeVolume(-0.1f)
        shift && !ctrl && event.key == Key.DirectionRight -> seekBy(5_000)
        shift && !ctrl && event.key == Key.DirectionLeft -> seekBy(-5_000)
        ctrl && !shift && event.key == Key.S -> player.player.shuffleModeEnabled = !player.player.shuffleModeEnabled
        ctrl && !shift && event.key == Key.R -> player.toggleRepeatMode()
        ctrl && !shift && event.key == Key.M -> player.toggleMute()
        else -> return false
    }
    return true
}
