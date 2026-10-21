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
            event.type == KeyEventType.KeyDown && event.key == Key.Escape && BackDispatcher.dispatch()
        },
    ) {
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
