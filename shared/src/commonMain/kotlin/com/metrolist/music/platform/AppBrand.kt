package com.metrolist.music.platform

import java.io.File

/**
 * DESKTOP: the desktop fork's own name. Kotlin package names stay `com.metrolist.*` so that monthly
 * upstream merges stay clean; only what users see (window, installer, folders, files) uses this.
 */
object AppBrand {
    /** ASCII form: folders, executable, file names. */
    const val NAME = "Lindale"

    /** Display form (Quenya "lindalë" = music): window title and about screens. */
    const val DISPLAY_NAME = "Lindalë"

    /** Name used before the rename; its folders are moved over once. */
    const val LEGACY_NAME = "Metrolist"

    /** Lindale's own repository: releases, update checks, about screen. */
    const val REPO_URL = "https://github.com/supdocquart/Lindale"

    /** GitHub REST base for [REPO_URL]. */
    const val REPO_API = "https://api.github.com/repos/supdocquart/Lindale"

    /**
     * Moves %APPDATA%\Metrolist and %LOCALAPPDATA%\Metrolist to the new name, once. Must run before
     * anything opens files there (first thing in main). When the new folder already exists, only the
     * entries it lacks are moved, so an interrupted move finishes on the next start.
     */
    fun migrateLegacyFolders() {
        if (System.getProperty("metrolist.dataDir") != null) return
        listOfNotNull(System.getenv("APPDATA"), System.getenv("LOCALAPPDATA")).distinct().forEach { root ->
            runCatching { migrate(File(root, LEGACY_NAME), File(root, NAME)) }
        }
    }

    private fun migrate(old: File, new: File) {
        if (!old.isDirectory) return
        if (!new.exists() && old.renameTo(new)) return
        new.mkdirs()
        old.listFiles().orEmpty().forEach { child ->
            val target = File(new, child.name)
            if (!target.exists()) child.renameTo(target)
        }
    }
}

/**
 * DESKTOP: where Lindale keeps its files. Windows: %APPDATA%\Lindale (settings, database) and
 * %LOCALAPPDATA%\Lindale (cache, logs). Linux: the XDG folders, ~/.local/share/lindale and
 * ~/.cache/lindale, unless XDG_DATA_HOME / XDG_CACHE_HOME say otherwise.
 */
object AppDirs {
    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private val home: String get() = System.getProperty("user.home")

    private fun xdg(variable: String, fallback: String): File =
        File(System.getenv(variable)?.takeIf { it.isNotBlank() } ?: "$home/$fallback", AppBrand.NAME.lowercase())

    /** Settings, database, tokens. */
    val roaming: File
        get() = if (isWindows) File(System.getenv("APPDATA") ?: home, AppBrand.NAME) else xdg("XDG_DATA_HOME", ".local/share")

    /** Disposable or machine-local files: caches, logs, native libraries. */
    val local: File
        get() = if (isWindows) File(System.getenv("LOCALAPPDATA") ?: home, AppBrand.NAME) else xdg("XDG_CACHE_HOME", ".cache")
}

