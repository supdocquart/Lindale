@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.metrolist.music.MetrolistApp
import com.metrolist.music.db.createDesktopDatabase
import com.metrolist.music.playback.DesktopPlayerConnection
import com.metrolist.music.platform.initDesktopPlatform

fun main(args: Array<String>) {
    // DESKTOP: the "Sign in with Google" window runs in its own process (same launcher + this flag),
    // so a JCEF crash cannot take the app down. It must not touch the app's log files.
    if (JcefGoogleLogin.HELPER_FLAG in args) {
        JcefGoogleLogin.runHelperProcess(args)
        return
    }
    // DESKTOP: rename Metrolist -> Lindale; move the old data folders before anything opens them.
    com.metrolist.music.platform.AppBrand.migrateLegacyFolders()
    // Lets a launcher pick Skia's backend (e.g. OPENGL, SOFTWARE) for memory comparisons.
    System.getenv("METROLIST_RENDER_API")?.takeIf { it.isNotBlank() }?.let { System.setProperty("skiko.renderApi", it) }
    // DESKTOP: stop AWT from erasing the window to white before Skia paints (white flash on start/resize).
    System.setProperty("sun.awt.noerasebackground", "true")
    DebugLog.install()
    com.metrolist.music.platform.StartupTrace.mark("main_debuglog_ready")
    runApp()
}

private fun runApp() = application {
    initDesktopPlatform()
    // DESKTOP: enables the "Sign in with Google" window when JCEF is present.
    remember { JcefGoogleLogin.install() }

    // DESKTOP: Restore the last window size, position and maximized state.
    val windowState = remember { WindowStateStore.load() }
    // DESKTOP: keep the window hidden until the custom title bar is installed, so it does not appear
    // small/white first and then jump to its final layout.
    var windowReady by remember { androidx.compose.runtime.mutableStateOf(false) }
    val database = remember { com.metrolist.music.platform.StartupTrace.span("database_open") { createDesktopDatabase() } }
    val playerConnection = remember(database) {
        com.metrolist.music.platform.StartupTrace.span("player_create") { DesktopPlayerConnection(database) }
    }
    remember(playerConnection) {
        com.metrolist.music.platform.StartupTrace.span("discord_start") {
            com.metrolist.music.discord.DesktopDiscordPresence(playerConnection, database).also { it.start() }
        }
    }
    remember(playerConnection) {
        com.metrolist.music.platform.StartupTrace.span("scrobbler_start") {
            com.metrolist.music.playback.DesktopScrobbler(playerConnection).also { it.start() }
        }
    }

    remember(playerConnection) {
        com.metrolist.music.platform.StartupTrace.span("media_session_start") {
            WindowsMediaSession(playerConnection).also { it.start() }
        }

    Window(
        onCloseRequest = {
            // DESKTOP: Persist the window geometry before the player shuts down.
            WindowStateStore.save(windowState)
            playerConnection.release()
            exitApplication()
        },
        title = com.metrolist.music.platform.AppBrand.DISPLAY_NAME,
        // DESKTOP: taskbar/title-bar icon (the exe icon comes from desktop/icon.ico via jpackage).
        icon = remember {
            androidx.compose.ui.graphics.painter.BitmapPainter(
                androidx.compose.ui.res.useResource("lindale.png") { androidx.compose.ui.res.loadImageBitmap(it) },
            )
        },
        state = windowState,
        visible = windowReady,
        onKeyEvent = { event ->
            when {
                event.type != KeyEventType.KeyDown -> false
                event.key == Key.Escape -> BackDispatcher.dispatch()
                else -> false
            }
        },
    ) {
        // DESKTOP: shortcuts are read before Compose focus handling; Window.onKeyEvent only saw keys
        // when a Compose element held focus, so most of them seemed dead.
        DisposableEffect(window) {
            val dispatcher = java.awt.KeyEventDispatcher { e ->
                e.id == java.awt.event.KeyEvent.KEY_PRESSED &&
                    javax.swing.SwingUtilities.getWindowAncestor(e.component) === window &&
                    handleShortcut(e, playerConnection)
            }
            val kfm = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
            kfm.addKeyEventDispatcher(dispatcher)
            onDispose { kfm.removeKeyEventDispatcher(dispatcher) }
        }
        // Title bar follows the app theme (see WindowChrome / WindowsTitleBar / JbrTitleBar).
        DisposableEffect(window) {
            // DESKTOP: dark fill behind Compose until the first frame (instead of white).
            window.background = java.awt.Color(0x12, 0x12, 0x12)
            window.contentPane.background = java.awt.Color(0x12, 0x12, 0x12)
            val customTitleBar = JbrTitleBar.install(window)
            windowReady = true
            com.metrolist.music.platform.StartupTrace.mark("window_ready")
            com.metrolist.music.platform.WindowChrome.applier = { argb, dark ->
                if (customTitleBar) {
                    // DESKTOP: the native caption is gone; only its buttons need light/dark icons.
                    JbrTitleBar.setDark(dark)
                } else {
                    java.awt.EventQueue.invokeLater {
                        com.metrolist.music.platform.WindowsTitleBar.apply(window, argb, dark)
                    }
                }
            }
            com.metrolist.music.platform.WindowChrome.reapply()
            // The native handle may not exist on the first composition; apply again once shown.
            val opened = object : java.awt.event.WindowAdapter() {
                override fun windowOpened(e: java.awt.event.WindowEvent?) {
                    com.metrolist.music.platform.WindowChrome.reapply()
                }
            }
            window.addWindowListener(opened)
            // Native button widths follow the window DPI, e.g. after moving to another monitor.
            val resized = object : java.awt.event.ComponentAdapter() {
                override fun componentResized(e: java.awt.event.ComponentEvent?) {
                    JbrTitleBar.refreshInsets()
                }
            }
            window.addComponentListener(resized)
            onDispose {
                window.removeWindowListener(opened)
                window.removeComponentListener(resized)
                JbrTitleBar.dispose()
                com.metrolist.music.platform.WindowChrome.applier = null
            }
        }
        LaunchedEffect(Unit) {
            androidx.compose.runtime.withFrameNanos { }
            com.metrolist.music.platform.StartupTrace.mark("first_frame")
        }
        val jbrTitleBar = com.metrolist.music.platform.WindowChrome.customTitleBar
        LaunchedEffect(window, jbrTitleBar) {
            if (!jbrTitleBar) return@LaunchedEffect
            // JBR applies the custom title bar asynchronously, so the native button insets can
            // still read 0 on the first frames. Retry briefly instead of polling forever.
            repeat(10) {
                JbrTitleBar.refreshInsets()
                delay(100)
            }
        }
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
        // DESKTOP: counts open text input sessions, so typing never triggers player shortcuts.
        androidx.compose.ui.platform.InterceptPlatformTextInput(
            interceptor = TextInputTracker,
        ) {
            MetrolistApp(
                database = database,
                playerConnection = playerConnection,
            )
        }
    }
}

private object TextInputTracker : androidx.compose.ui.platform.PlatformTextInputInterceptor {
    private val sessions = java.util.concurrent.atomic.AtomicInteger()
    val typing: Boolean get() = sessions.get() > 0

    override suspend fun interceptStartInputMethod(
        request: androidx.compose.ui.platform.PlatformTextInputMethodRequest,
        nextHandler: androidx.compose.ui.platform.PlatformTextInputSession,
    ): Nothing {
        sessions.incrementAndGet()
        try {
            nextHandler.startInputMethod(request)
        } finally {
            sessions.decrementAndGet()
        }
    }
}

/** Spotify-style shortcuts. Returns true when the key was used (so Compose does not also get it). */
private fun handleShortcut(e: java.awt.event.KeyEvent, player: DesktopPlayerConnection): Boolean {
    if (e.isAltDown || e.isMetaDown) return false
    val ctrl = e.isControlDown
    val shift = e.isShiftDown
    val code = e.keyCode
    if (ctrl && !shift && (code == java.awt.event.KeyEvent.VK_K || code == java.awt.event.KeyEvent.VK_F)) {
        GlobalSearch.focus()
        return true
    }
    // In a text field Space, arrows, Ctrl+arrows and Ctrl+S/R/M are editing keys.
    if (TextInputTracker.typing) return false
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
        code == java.awt.event.KeyEvent.VK_SPACE && !ctrl && !shift -> player.togglePlayPause()
        ctrl && !shift && code == java.awt.event.KeyEvent.VK_RIGHT -> player.seekToNext()
        ctrl && !shift && code == java.awt.event.KeyEvent.VK_LEFT -> player.seekToPrevious()
        ctrl && !shift && code == java.awt.event.KeyEvent.VK_UP -> changeVolume(0.1f)
        ctrl && !shift && code == java.awt.event.KeyEvent.VK_DOWN -> changeVolume(-0.1f)
        shift && !ctrl && code == java.awt.event.KeyEvent.VK_RIGHT -> seekBy(5_000)
        shift && !ctrl && code == java.awt.event.KeyEvent.VK_LEFT -> seekBy(-5_000)
        ctrl && !shift && code == java.awt.event.KeyEvent.VK_S -> player.player.shuffleModeEnabled = !player.player.shuffleModeEnabled
        ctrl && !shift && code == java.awt.event.KeyEvent.VK_R -> player.toggleRepeatMode()
        ctrl && !shift && code == java.awt.event.KeyEvent.VK_M -> player.toggleMute()
        else -> return false
    }
    return true
}
