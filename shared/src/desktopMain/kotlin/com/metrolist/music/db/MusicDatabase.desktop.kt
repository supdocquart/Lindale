package com.metrolist.music.db

import androidx.room.Room
import androidx.sqlite.execSQL
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
        .addMigrations(*desktopMigrations)
        .build()

    return MusicDatabase(internalDb)
}

/**
 * DESKTOP: schema 36 -> 38 (upstream AutoMigrations 36->37, 37->38: three speed dial columns and the
 * artist page cache). Desktop databases labelled 36 were created from the 13.7.0 entities and may
 * already have these columns, so each column is added only when missing.
 */
private val schema38Columns = listOf(
    "speed_dial_item" to "albumId",
    "speed_dial_item" to "subtitleIds",
    "speed_dial_item" to "albumName",
    "artist" to "cachedPageJson",
)

private fun addMissingSchema38Columns(connection: androidx.sqlite.SQLiteConnection) {
    for ((table, column) in schema38Columns) {
        val existing = connection.prepare("PRAGMA table_info(`$table`)").use { st ->
            buildSet { while (st.step()) add(st.getText(1)) }
        }
        if (column !in existing) connection.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` TEXT")
    }
}

private val desktopMigrations = arrayOf(36 to 38, 36 to 37, 37 to 38).map { (from, to) ->
    object : androidx.room.migration.Migration(from, to) {
        override fun migrate(connection: androidx.sqlite.SQLiteConnection) = addMissingSchema38Columns(connection)
    }
}.toTypedArray()
