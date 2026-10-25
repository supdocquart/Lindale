import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import com.metrolist.music.platform.WindowChrome
import java.awt.Frame
import java.awt.Window

/**
 * DESKTOP: installs JBR's custom title bar so the app can draw a single Spotify-style row where
 * the native caption used to be. The frame stays decorated, so resize borders, Aero Snap, the
 * snap layouts flyout and the native min/max/close buttons keep working. When JBR or its window
 * decorations API is unavailable this returns false and Main.kt keeps the current DWM caption.
 */
object JbrTitleBar {
    private const val HEIGHT_DP = 48f

    private var titleBar: WindowDecorations.CustomTitleBar? = null
    private var lastInteractive: Boolean? = null

    val isActive: Boolean get() = titleBar != null

    fun install(window: Window): Boolean {
        if (isActive) return true
        if (window !is Frame) return false
        if (!runCatching { JBR.isAvailable() && JBR.isWindowDecorationsSupported() }.getOrDefault(false)) {
            return false
        }

        return runCatching {
            val decorations = JBR.getWindowDecorations()
            val bar = decorations.createCustomTitleBar()
            // JBR scales this value by the window's scale factor internally, so it is in dp.
            bar.height = HEIGHT_DP
            bar.putProperty("controls.dark", false)
            decorations.setCustomTitleBar(window, bar)

            titleBar = bar
            lastInteractive = null
            WindowChrome.titleBarHitTest = { interactive -> setInteractive(interactive) }
            WindowChrome.setCustomTitleBarActive(true)
            refreshInsets()
            true
        }.getOrElse {
            titleBar = null
            lastInteractive = null
            WindowChrome.titleBarHitTest = null
            WindowChrome.setCustomTitleBarActive(false)
            false
        }
    }

    fun setDark(dark: Boolean) {
        titleBar?.putProperty("controls.dark", dark)
    }

    /** Reads the native button insets; they are dp even though JBR exposes them as floats. */
    fun refreshInsets() {
        val bar = titleBar ?: return
        runCatching { WindowChrome.setTitleBarInsets(bar.leftInset, bar.rightInset) }
    }

    private fun setInteractive(interactive: Boolean) {
        // DESKTOP: JBR applies forceHitTest to the mouse event being processed, so it must be called on
        // every pointer event over the title bar, not only when the value changes.
        lastInteractive = interactive
        runCatching { titleBar?.forceHitTest(interactive) }
    }

    fun dispose() {
        titleBar = null
        lastInteractive = null
        WindowChrome.titleBarHitTest = null
        WindowChrome.setTitleBarInsets(0f, 0f)
        WindowChrome.setCustomTitleBarActive(false)
    }
}
