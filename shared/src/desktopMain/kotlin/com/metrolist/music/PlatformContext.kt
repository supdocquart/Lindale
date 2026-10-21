@file:JvmName("DesktopPlatformContextKt")
package com.metrolist.music

import android.content.Context
import java.io.File

class DesktopContext : Context() {
    override val cacheDir: File
        get() = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Metrolist/cache").apply { mkdirs() }

    override val filesDir: File
        get() = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Metrolist").apply { mkdirs() }
}

val defaultDesktopContext: Context = DesktopContext()

fun getDesktopPlatformContext(): Context = defaultDesktopContext
