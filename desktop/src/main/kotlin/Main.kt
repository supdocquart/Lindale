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
    ) {
        MetrolistApp(
            database = database,
            playerConnection = playerConnection,
        )
    }
}
