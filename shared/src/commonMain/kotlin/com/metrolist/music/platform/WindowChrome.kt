package com.metrolist.music.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

/**
 * DESKTOP: lets the app paint the OS window frame (title bar) in its own theme color so the
 * native caption doesn't look like a separate white strip. The desktop entry point installs
 * [applier]; the UI calls [set] whenever the theme changes.
 *
 * When JBR's custom title bar is available the native caption is replaced by a Compose row, so
 * [customTitleBar] tells the UI to use that layout and the inset values reserve room for the
 * native min/max/close buttons. On runtimes without JBR everything stays on the DWM path.
 */
object WindowChrome {
    var applier: ((captionArgb: Int, dark: Boolean) -> Unit)? = null

    /** True once the JBR custom title bar has been installed for the main window. */
    var customTitleBar by mutableStateOf(false)
        private set

    /** Space reserved on the left by native title bar controls (menu bar / RTL buttons). */
    var titleBarLeftInset by mutableStateOf(0.dp)
        private set

    /** Space reserved on the right by the native minimize/maximize/close buttons. */
    var titleBarRightInset by mutableStateOf(0.dp)
        private set

    /** Lets the Compose row switch JBR between native caption handling and normal client input. */
    var titleBarHitTest: ((interactive: Boolean) -> Unit)? = null

    private var last: Pair<Int, Boolean>? = null

    fun set(captionArgb: Int, dark: Boolean) {
        val next = captionArgb to dark
        if (next == last) return
        last = next
        applier?.invoke(captionArgb, dark)
    }

    /** Re-applies the last colors (e.g. once the native window handle exists). */
    fun reapply() {
        last?.let { (argb, dark) -> applier?.invoke(argb, dark) }
    }

    fun setCustomTitleBarActive(active: Boolean) {
        if (customTitleBar != active) customTitleBar = active
    }

    fun setTitleBarInsets(leftDp: Float, rightDp: Float) {
        val left = leftDp.coerceAtLeast(0f).dp
        val right = rightDp.coerceAtLeast(0f).dp
        if (titleBarLeftInset != left) titleBarLeftInset = left
        if (titleBarRightInset != right) titleBarRightInset = right
    }

    fun setTitleBarInteractive(interactive: Boolean) {
        titleBarHitTest?.invoke(interactive)
    }
}
