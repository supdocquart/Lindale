package com.metrolist.music.playback

import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.utils.get
import kotlinx.coroutines.flow.map
import java.io.ObjectOutputStream
import java.io.Serializable

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
