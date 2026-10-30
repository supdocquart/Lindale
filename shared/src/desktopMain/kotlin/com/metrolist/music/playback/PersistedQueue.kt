package com.metrolist.music.playback

import com.metrolist.music.playback.Timeline
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.music.constants.AudioNormalizationKey
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.constants.AudioQualityKey
import com.metrolist.music.constants.AutoLoadMoreKey
import com.metrolist.music.constants.AutoRadioQueueKey
import com.metrolist.music.constants.AutoSkipNextOnErrorKey
import com.metrolist.music.constants.AutoplayKey
import com.metrolist.music.constants.DisableLoadMoreWhenRepeatAllKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.LoudnessLevel
import com.metrolist.music.constants.LoudnessLevelKey
import com.metrolist.music.constants.PersistentQueueKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Event
import com.metrolist.music.db.entities.FormatEntity
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.platform.AppContext
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.nowLocalDateTime
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.Queue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.playback.queues.filterExplicit
import com.metrolist.music.playback.queues.filterVideoSongs
import com.metrolist.music.utils.InnerTubeXPlayer
import com.metrolist.music.utils.VideoId
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.normalizationGainMb
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.net.InetSocketAddress
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * DESKTOP: the shared [com.metrolist.music.models.MediaMetadata] model is not java.io.Serializable in
 * this port, so upstream's [com.metrolist.music.models.PersistQueue] cannot be written with
 * ObjectOutputStream. The queue is stored in this equivalent serializable snapshot (same file name and
 * fields) and rebuilt into media items on load.
 */
internal data class PersistedQueueSnapshot(
    val title: String?,
    val items: List<PersistedMetadata>,
    val mediaItemIndex: Int,
    val position: Long,
) : Serializable {
    // Pinned to the value these classes had before they moved to this file, so saved queues still load.
    companion object { private const val serialVersionUID: Long = -4297184183311310262L }
}

internal data class PersistedMetadata(
    val id: String,
    val title: String,
    val artists: List<PersistedArtist>,
    val duration: Int,
    val thumbnailUrl: String?,
    val albumId: String?,
    val albumTitle: String?,
    val setVideoId: String?,
    val musicVideoType: String?,
    val explicit: Boolean,
    val liked: Boolean,
    val libraryAddToken: String?,
    val libraryRemoveToken: String?,
    val suggestedBy: String?,
    val isEpisode: Boolean,
    val uploadEntityId: String?,
) : Serializable {
    // Pinned to the value these classes had before they moved to this file, so saved queues still load.
    companion object { private const val serialVersionUID: Long = 7705913053477534472L }


    fun toMediaMetadata() = MediaMetadata(
        id = id,
        title = title,
        artists = artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
        duration = duration,
        thumbnailUrl = thumbnailUrl,
        album = albumId?.let { MediaMetadata.Album(id = it, title = albumTitle.orEmpty()) },
        setVideoId = setVideoId,
        musicVideoType = musicVideoType,
        explicit = explicit,
        liked = liked,
        libraryAddToken = libraryAddToken,
        libraryRemoveToken = libraryRemoveToken,
        suggestedBy = suggestedBy,
        isEpisode = isEpisode,
        uploadEntityId = uploadEntityId,
    )
}

internal data class PersistedArtist(
    val id: String?,
    val name: String,
) : Serializable {
    // Pinned to the value these classes had before they moved to this file, so saved queues still load.
    companion object { private const val serialVersionUID: Long = -1548035657369626280L }
}

internal fun MediaMetadata.toPersistedMetadata() = PersistedMetadata(
    id = id,
    title = title,
    artists = artists.map { PersistedArtist(id = it.id, name = it.name) },
    duration = duration,
    thumbnailUrl = thumbnailUrl,
    albumId = album?.id,
    albumTitle = album?.title,
    setVideoId = setVideoId,
    musicVideoType = musicVideoType,
    explicit = explicit,
    liked = liked,
    libraryAddToken = libraryAddToken,
    libraryRemoveToken = libraryRemoveToken,
    suggestedBy = suggestedBy,
    isEpisode = isEpisode,
    uploadEntityId = uploadEntityId,
)
