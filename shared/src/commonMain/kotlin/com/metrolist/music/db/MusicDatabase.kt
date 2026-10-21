package com.metrolist.music.db
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
    version = 36,
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

    fun transaction(block: suspend MusicDatabase.() -> Unit): kotlinx.coroutines.Job =
        scope.launch(Dispatchers.IO) { block(this@MusicDatabase) }

    suspend fun withTransaction(block: suspend MusicDatabase.() -> Unit) {
        block(this@MusicDatabase)
    }

    companion object {
        const val DB_NAME = "music.db"
        const val DB_VERSION = 36

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






