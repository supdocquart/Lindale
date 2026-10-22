package com.metrolist.music.platform

/**
 * DESKTOP: lets the app paint the OS window frame (title bar) in its own theme color so the
 * native caption doesn't look like a separate white strip. The desktop entry point installs
 * [applier]; the UI calls [set] whenever the theme changes.
 */
object WindowChrome {
    var applier: ((captionArgb: Int, dark: Boolean) -> Unit)? = null

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
}
