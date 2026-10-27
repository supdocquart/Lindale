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
