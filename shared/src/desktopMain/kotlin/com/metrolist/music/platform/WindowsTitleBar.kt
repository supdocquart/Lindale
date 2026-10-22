package com.metrolist.music.platform

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * Windows 11 DWM attributes: dark caption, caption/border/text colors. Keeps the native title
 * bar (snap layouts, resize, min/max/close) but colors it like the app. Older Windows versions
 * ignore the unsupported attributes.
 */
object WindowsTitleBar {
    private interface Dwmapi : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: Pointer, size: Int): Int
    }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    private val dwm: Dwmapi? by lazy {
        if (!isWindows) null else runCatching { Native.load("dwmapi", Dwmapi::class.java) }.getOrNull()
    }

    /** ARGB (Compose) -> COLORREF 0x00BBGGRR. */
    private fun colorRef(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }

    private fun setInt(hwnd: Pointer, attribute: Int, value: Int) {
        val mem = Memory(4).apply { setInt(0, value) }
        runCatching { dwm?.DwmSetWindowAttribute(hwnd, attribute, mem, 4) }
    }

    fun apply(window: java.awt.Window, captionArgb: Int, dark: Boolean) {
        if (!isWindows || !window.isDisplayable) return
        val hwnd = runCatching { Native.getWindowPointer(window) }.getOrNull() ?: return
        setInt(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
        setInt(hwnd, DWMWA_CAPTION_COLOR, colorRef(captionArgb))
        setInt(hwnd, DWMWA_BORDER_COLOR, colorRef(captionArgb))
        setInt(hwnd, DWMWA_TEXT_COLOR, if (dark) 0x00E6E6E6 else 0x00202020)
    }
}
