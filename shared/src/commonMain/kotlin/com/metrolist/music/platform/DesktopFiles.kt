package com.metrolist.music.platform

import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import java.io.FilenameFilter

/** Native open/save dialogs plus helpers that map URIs used by the UI layer to files. */
object DesktopFiles {
    private val home: String get() = System.getProperty("user.home")

    /** Roaming app data (settings, database, tokens). */
    val dataDir: File
        get() = File(System.getenv("APPDATA") ?: home, "Metrolist").apply { mkdirs() }

    /** Local, disposable cache (images, shared files, downloads cache). */
    val cacheDir: File
        get() = File(System.getenv("LOCALAPPDATA") ?: home, "Metrolist/cache").apply { mkdirs() }

    private fun ownerFrame(): Frame? {
        val focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        return (focused as? Frame) ?: Frame.getFrames().firstOrNull { it.isVisible }
    }

    /** Maps MIME types to a Windows wildcard filter, or null for all files. */
    fun extensionsFor(mimeTypes: Array<out String>?): List<String>? {
        if (mimeTypes.isNullOrEmpty()) return null
        val result = linkedSetOf<String>()
        for (mime in mimeTypes) {
            when {
                mime == "*/*" || mime == "application/octet-stream" -> return null
                mime.startsWith("image/") -> result += listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
                mime.startsWith("audio/") -> result += listOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac", "wma", "m3u", "m3u8")
                mime.contains("csv") -> result += listOf("csv", "txt")
                mime == "text/plain" -> result += listOf("txt", "csv")
                mime.startsWith("text/") -> result += "txt"
                mime == "application/json" -> result += "json"
                mime == "application/zip" -> result += "zip"
                else -> return null
            }
        }
        return result.toList().ifEmpty { null }
    }

    private fun dialog(title: String, mode: Int, extensions: List<String>?): FileDialog {
        val owner = ownerFrame()
        val dialog = if (owner != null) FileDialog(owner, title, mode) else FileDialog(null as Frame?, title, mode)
        if (extensions != null && mode == FileDialog.LOAD) {
            dialog.file = extensions.joinToString(";") { "*.$it" }
            dialog.filenameFilter = FilenameFilter { _, name ->
                extensions.any { name.endsWith(".$it", ignoreCase = true) }
            }
        }
        return dialog
    }

    fun openFile(title: String = "Dosya seç", extensions: List<String>? = null): File? {
        val d = dialog(title, FileDialog.LOAD, extensions)
        d.isVisible = true
        val name = d.file ?: return null
        return File(d.directory, name).takeIf { it.isFile }
    }

    fun openFiles(title: String = "Dosya seç", extensions: List<String>? = null): List<File> {
        val d = dialog(title, FileDialog.LOAD, extensions)
        d.isMultipleMode = true
        d.isVisible = true
        return d.files.orEmpty().filter { it.isFile }
    }

    fun saveFile(title: String = "Kaydet", suggestedName: String? = null): File? {
        val d = dialog(title, FileDialog.SAVE, null)
        if (!suggestedName.isNullOrBlank()) d.file = suggestedName
        d.isVisible = true
        val name = d.file ?: return null
        return File(d.directory, name)
    }

    /** Resolves file: URIs, plain paths and java.io.File instances; returns null for anything else. */
    fun toFile(uri: Any?): File? = when (uri) {
        null -> null
        is File -> uri
        else -> {
            val s = uri.toString()
            when {
                s.startsWith("file:") -> runCatching { File(java.net.URI(s)) }.getOrNull()
                s.contains("://") -> null
                s.isNotBlank() -> File(s)
                else -> null
            }
        }
    }
}
