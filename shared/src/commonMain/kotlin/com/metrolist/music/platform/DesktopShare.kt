package com.metrolist.music.platform

import com.metrolist.music.utils.ResourceText
import com.metrolist.music.utils.Toast
import com.metrolist.music.utils.ToastHost
import metrolist.shared.generated.resources.Res
import metrolist.shared.generated.resources.copied
import timber.log.Timber
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.net.URLEncoder

/**
 * Desktop replacements for the system share sheet and "open with" flows:
 * text goes to the clipboard, files are offered through a save dialog,
 * links open in the default browser.
 */
object DesktopShare {
    fun copyToClipboard(text: CharSequence, notify: Boolean = true) {
        runCatching {
            val selection = StringSelection(text.toString())
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        }.onFailure { Timber.w(it, "clipboard write failed") }
        if (notify) ToastHost.post(ResourceText.get(Res.string.copied))
    }

    fun shareText(text: String) = copyToClipboard(text)

    /** Lets the user save a copy of [file]; returns the chosen destination. */
    fun shareFile(file: File?, suggestedName: String? = null): File? {
        if (file == null || !file.isFile) return null
        val target = DesktopFiles.saveFile(suggestedName = suggestedName ?: file.name) ?: return null
        return runCatching {
            file.copyTo(target, overwrite = true)
            ToastHost.post(target.absolutePath, Toast.LENGTH_LONG)
            target
        }.onFailure { Timber.w(it, "share file copy failed") }.getOrNull()
    }

    fun shareFile(uri: Any?, suggestedName: String? = null): File? =
        shareFile(DesktopFiles.toFile(uri), suggestedName)

    /** DESKTOP: only web and mail links may be opened; anything else (file:, UNC paths, custom schemes) is refused. */
    fun isSafeExternalUrl(url: String?): Boolean {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        return runCatching {
            val uri = URI(trimmed)
            when (uri.scheme?.lowercase()) {
                "http", "https" -> !uri.host.isNullOrBlank()
                "mailto" -> true
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun schemeOf(url: String): String? = runCatching { URI(url.trim()).scheme }.getOrNull()

    fun openUrl(url: String?) {
        if (url.isNullOrBlank()) return
        // DESKTOP: never hand a file path, UNC share or custom scheme to the shell protocol handler.
        if (!isSafeExternalUrl(url)) {
            Timber.w("openUrl: refused non-web link (scheme=%s)", schemeOf(url))
            return
        }
        val scheme = schemeOf(url)
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            } else {
                ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
            }
        }.onFailure { Timber.w(it, "openUrl failed (scheme=%s)", scheme) }
    }

    fun webSearch(query: String) =
        openUrl("https://www.google.com/search?q=" + URLEncoder.encode(query, Charsets.UTF_8))

    fun openFolder(dir: File) {
        runCatching { Desktop.getDesktop().open(dir) }
            .onFailure { Timber.w(it, "openFolder failed: %s", dir) }
    }
}
