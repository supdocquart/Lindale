package com.metrolist.music.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.metrolist.music.defaultDesktopContext
import kotlinx.coroutines.Dispatchers
import java.io.File

fun createDesktopDatabase(): MusicDatabase {
    val dbFile = File(defaultDesktopContext.filesDir, "song.db")
    dbFile.parentFile?.mkdirs()

    val internalDb = Room.databaseBuilder<InternalDatabase>(
        name = dbFile.absolutePath
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    return MusicDatabase(internalDb)
}
