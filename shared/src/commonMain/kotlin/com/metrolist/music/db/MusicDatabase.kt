package com.metrolist.music.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.metrolist.music.db.daos.SpeedDialDao
import com.metrolist.music.db.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Database(
    entities = [
        SongEntity::class, ArtistEntity::class, AlbumEntity::class, PlaylistEntity::class,
        PlaylistSongMap::class, SearchHistory::class, FormatEntity::class, LyricsEntity::class,
        SongArtistMap::class, SongAlbumMap::class, AlbumArtistMap::class, RelatedSongMap::class,
        PlayCountEntity::class, Event::class, SetVideoIdEntity::class, RecognitionHistory::class,
        SpeedDialItem::class, PodcastEntity::class
    ],
    views = [
        SortedSongArtistMap::class,
        SortedSongAlbumMap::class,
        PlaylistSongMapPreview::class
    ],
    version = 38, // DESKTOP: same as upstream 13.7.0, so its Android backups restore
    exportSchema = false
)
@TypeConverters(Converters::class)
@ConstructedBy(MusicDatabaseConstructor::class)
abstract class InternalDatabase : RoomDatabase() {
    abstract val dao: DatabaseDao
    abstract val speedDialDao: SpeedDialDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object MusicDatabaseConstructor : RoomDatabaseConstructor<InternalDatabase>

class MusicDatabase(
    val delegate: InternalDatabase
) : DatabaseDao by delegate.dao {
    val speedDialDao: SpeedDialDao
        get() = delegate.speedDialDao

    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    fun close() {
        scope.cancel()
        delegate.close()
    }

    fun query(block: suspend MusicDatabase.() -> Unit): kotlinx.coroutines.Job =
        scope.launch(Dispatchers.IO) { block(this@MusicDatabase) }

    // Like upstream: runs in the background, but inside a real database transaction.
    fun transaction(block: suspend MusicDatabase.() -> Unit): kotlinx.coroutines.Job =
        scope.launch(Dispatchers.IO) { withTransaction(block) }

    // DESKTOP: Room KMP has no runInTransaction/withTransaction; DAO calls made inside this
    // block run on the same writer connection, so they commit or roll back together.
    suspend fun withTransaction(block: suspend MusicDatabase.() -> Unit) {
        delegate.useWriterConnection { transactor ->
            transactor.immediateTransaction { block(this@MusicDatabase) }
        }
    }

    companion object {
        const val DB_NAME = "music.db"
        const val DB_VERSION = 38

        // DESKTOP: oldest backup schema the desktop migrations (MusicDatabase.desktop.kt) can upgrade.
        const val MIN_RESTORE_DB_VERSION = 36

        fun readDatabaseVersion(dbPath: String): Int {
            return try {
                val file = java.io.File(dbPath)
                if (!file.exists() || file.length() < 64) return -1
                java.io.RandomAccessFile(file, "r").use { raf ->
                    raf.seek(60)
                    raf.readInt()
                }
            } catch (e: Exception) {
                -1
            }
        }
    }
}






