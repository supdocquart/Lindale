package com.metrolist.music.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.metrolist.music.utils.ResourceText
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** App-wide services the UI and data layers need: app folders, file streams and string resources. */
open class AppContext {
    val cacheDir: File get() = DesktopFiles.cacheDir
    val filesDir: File get() = DesktopFiles.dataDir

    fun getDatabasePath(name: String): File = File(filesDir, name)

    /** Accepts file: URIs, paths or [File]s. */
    fun openInputStream(uri: Any): InputStream? =
        DesktopFiles.toFile(uri)?.takeIf { it.isFile }?.inputStream()

    fun openOutputStream(uri: Any): OutputStream? =
        DesktopFiles.toFile(uri)?.let { file ->
            file.parentFile?.mkdirs()
            file.outputStream()
        }

    fun getString(resId: Any, vararg args: Any?): String = ResourceText.resolve(resId, *args)

    companion object {
        val Default = AppContext()
    }
}

val LocalAppContext = staticCompositionLocalOf { AppContext.Default }
