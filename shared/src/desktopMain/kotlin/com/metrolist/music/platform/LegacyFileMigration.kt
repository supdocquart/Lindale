package com.metrolist.music.platform

import timber.log.Timber
import java.io.File

/**
 * DESKTOP: early desktop builds used their own file names (song.db, settings.preferences_pb in the data root).
 * Backup/restore and upstream code expect Android's layout (music.db, datastore/settings.preferences_pb), so the
 * old files are moved once. A move is all-or-nothing: if any part fails, what was moved is put back.
 */
object LegacyFileMigration {
    /** Moves [from] (+ "-wal", "-shm" side files when [withSqliteSideFiles]) to [to] if [to] does not exist yet. */
    fun moveOnce(from: File, to: File, withSqliteSideFiles: Boolean = false): Boolean {
        if (!from.exists() || to.exists()) return false
        val suffixes = if (withSqliteSideFiles) listOf("", "-wal", "-shm") else listOf("")
        val moved = mutableListOf<Pair<File, File>>()
        to.parentFile?.mkdirs()
        for (suffix in suffixes) {
            val src = File(from.path + suffix)
            if (!src.exists()) continue
            val dst = File(to.path + suffix)
            if (dst.exists() || !src.renameTo(dst)) {
                Timber.e("legacy migration: could not move %s, rolling back", src.name)
                moved.asReversed().forEach { (a, b) -> b.renameTo(a) }
                return false
            }
            moved += src to dst
        }
        Timber.i("legacy migration: moved %s -> %s", from.name, to.path.substringAfterLast(File.separatorChar))
        return true
    }
}
