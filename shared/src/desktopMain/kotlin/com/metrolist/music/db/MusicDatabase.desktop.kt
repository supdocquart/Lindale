package com.metrolist.music.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.metrolist.music.platform.AppContext
import kotlinx.coroutines.Dispatchers
import java.io.File

fun createDesktopDatabase(): MusicDatabase {
    // DESKTOP: same file name as Android (MusicDatabase.DB_NAME) so backup/restore, which use
    // AppContext.getDatabasePath(DB_NAME), find the live database. Early builds used "song.db".
    val filesDir = AppContext.Default.filesDir
    val legacyFile = File(filesDir, "song.db")
    val targetFile = File(filesDir, MusicDatabase.DB_NAME)
    targetFile.parentFile?.mkdirs()
    // If the move fails, keep opening the old file rather than an empty library.
    val dbFile =
        if (legacyFile.exists() && !targetFile.exists() &&
            !com.metrolist.music.platform.LegacyFileMigration.moveOnce(legacyFile, targetFile, withSqliteSideFiles = true)
        ) legacyFile
        else targetFile

    val internalDb = Room.databaseBuilder<InternalDatabase>(
        name = dbFile.absolutePath
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    return MusicDatabase(internalDb)
}
