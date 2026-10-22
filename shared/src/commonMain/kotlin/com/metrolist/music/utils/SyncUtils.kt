package com.metrolist.music.utils
import com.metrolist.music.platform.AppContext

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.AlbumEntity
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.models.toMediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber

class SyncUtils(
    val context: AppContext,
    val database: MusicDatabase
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    val isSyncing = MutableStateFlow(false)

    fun tryAutoSync() {
        if (!isSyncing.value) {
            performFullSync()
        }
    }

    fun performFullSync() {
        scope.launch {
            performFullSyncSuspend()
        }
    }

    suspend fun performFullSyncSuspend() {
        if (isSyncing.value) return
        isSyncing.value = true
        try {
            syncLikedSongs()
            syncSavedPlaylists()
            syncLikedAlbums()
            syncArtistsSubscriptions()
        } catch (e: Exception) {
            Timber.e(e, "Error during full sync")
        } finally {
            isSyncing.value = false
        }
    }

    private suspend fun fetchAllPlaylistSongs(playlistId: String): List<SongItem> {
        val result = mutableListOf<SongItem>()
        val initialPage = YouTube.playlist(playlistId).getOrNull() ?: return emptyList()
        result.addAll(initialPage.songs)
        var continuation = initialPage.continuation
        while (!continuation.isNullOrBlank()) {
            val continuationPage = YouTube.playlistContinuation(continuation).getOrNull() ?: break
            result.addAll(continuationPage.songs)
            continuation = continuationPage.continuation
        }
        return result
    }

    private suspend fun fetchAllLibraryItems(browseId: String): List<YTItem> {
        val result = mutableListOf<YTItem>()
        val initialPage = YouTube.library(browseId).getOrNull() ?: return emptyList()
        result.addAll(initialPage.items)
        var continuation = initialPage.continuation
        while (!continuation.isNullOrBlank()) {
            val continuationPage = YouTube.libraryContinuation(continuation).getOrNull() ?: break
            result.addAll(continuationPage.items)
            continuation = continuationPage.continuation
        }
        return result
    }

    fun syncLikedSongs() {
        scope.launch {
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val songs = fetchAllPlaylistSongs("LM")
                songs.forEach { songItem ->
                    val metadata = songItem.toMediaMetadata()
                    database.insert(metadata) { entity ->
                        entity.copy(
                            liked = true,
                            likedDate = entity.likedDate ?: now,
                            inLibrary = entity.inLibrary ?: now
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync liked songs")
            }
        }
    }

    fun syncLibrarySongs() {
        scope.launch {
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val items = fetchAllLibraryItems("FEmusic_liked_videos")
                items.filterIsInstance<SongItem>().forEach { songItem ->
                    val metadata = songItem.toMediaMetadata()
                    database.insert(metadata) { entity ->
                        entity.copy(inLibrary = entity.inLibrary ?: now)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync library songs")
            }
        }
    }

    fun syncUploadedSongs() {
        scope.launch {
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val items = fetchAllLibraryItems("FEmusic_library_privately_owned_tracks")
                items.filterIsInstance<SongItem>().forEach { songItem ->
                    val metadata = songItem.toMediaMetadata()
                    database.insert(metadata) { entity ->
                        entity.copy(isUploaded = true, inLibrary = entity.inLibrary ?: now)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync uploaded songs")
            }
        }
    }

    fun syncLikedAlbums() {
        scope.launch {
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val items = fetchAllLibraryItems("FEmusic_library_corpus_albums")
                items.filterIsInstance<AlbumItem>().forEach { albumItem ->
                    val entity = AlbumEntity(
                        id = albumItem.browseId,
                        playlistId = albumItem.playlistId,
                        title = albumItem.title,
                        year = albumItem.year,
                        thumbnailUrl = albumItem.thumbnail,
                        songCount = 0,
                        duration = 0,
                        bookmarkedAt = now,
                    )
                    if (database.insert(entity) == -1L) {
                        val existing = database.albumEntity(albumItem.browseId)
                        if (existing != null && existing.bookmarkedAt == null) {
                            database.update(existing.copy(bookmarkedAt = now))
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync liked albums")
            }
        }
    }

    fun syncUploadedAlbums() {
        scope.launch {
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val items = fetchAllLibraryItems("FEmusic_library_privately_owned_releases")
                items.filterIsInstance<AlbumItem>().forEach { albumItem ->
                    val entity = AlbumEntity(
                        id = albumItem.browseId,
                        playlistId = albumItem.playlistId,
                        title = albumItem.title,
                        year = albumItem.year,
                        thumbnailUrl = albumItem.thumbnail,
                        songCount = 0,
                        duration = 0,
                        isUploaded = true,
                        bookmarkedAt = now,
                    )
                    if (database.insert(entity) == -1L) {
                        val existing = database.albumEntity(albumItem.browseId)
                        if (existing != null && existing.bookmarkedAt == null) {
                            database.update(existing.copy(bookmarkedAt = now))
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync uploaded albums")
            }
        }
    }

    fun syncArtistsSubscriptions() {
        scope.launch {
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val items = fetchAllLibraryItems("FEmusic_library_corpus_artists")
                items.filterIsInstance<ArtistItem>().forEach { artistItem ->
                    val entity = ArtistEntity(
                        id = artistItem.id,
                        name = artistItem.title,
                        thumbnailUrl = artistItem.thumbnail,
                        channelId = artistItem.channelId,
                        bookmarkedAt = now,
                    )
                    database.insert(entity)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync artists")
            }
        }
    }

    fun syncSavedPlaylists() {
        scope.launch {
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val items = fetchAllLibraryItems("FEmusic_liked_playlists")
                items.filterIsInstance<PlaylistItem>().forEach { playlistItem ->
                    val entity = PlaylistEntity(
                        id = playlistItem.id,
                        name = playlistItem.title,
                        browseId = playlistItem.id,
                        createdAt = now,
                        lastUpdateTime = now,
                        thumbnailUrl = playlistItem.thumbnail,
                        bookmarkedAt = now,
                    )
                    database.insert(entity)
                    val existing = database.playlist(playlistItem.id).firstOrNull()?.playlist
                    if (existing != null) {
                        database.update(existing.copy(bookmarkedAt = now), playlistItem)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync saved playlists")
            }
        }
    }

    fun syncAutoSyncPlaylists() {
        syncSavedPlaylists()
    }

    suspend fun syncPlaylistSuspend(browseId: String, playlistId: String? = null) {
        try {
            val pid = playlistId ?: browseId
            val page = YouTube.playlist(browseId).getOrNull() ?: return
            page.songs.forEachIndexed { index, songItem ->
                val metadata = songItem.toMediaMetadata()
                database.insert(metadata)
                database.insert(
                    PlaylistSongMap(
                        playlistId = pid,
                        songId = songItem.id,
                        position = index,
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync playlist $browseId")
        }
    }

    fun likeSong(s: Any?) {
        when (s) {
            is Song -> likeSong(s.song)
            is SongEntity -> likeSong(s, !s.liked)
            is SongItem -> {
                val metadata = s.toMediaMetadata()
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                scope.launch {
                    database.insert(metadata) { it.copy(liked = true, likedDate = now, inLibrary = it.inLibrary ?: now) }
                    YouTube.likeVideo(s.id, true)
                }
            }
        }
    }

    fun likeSong(s: SongEntity, liked: Boolean = true) {
        scope.launch {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            database.update(
                s.copy(
                    liked = liked,
                    likedDate = if (liked) s.likedDate ?: now else null,
                    inLibrary = if (liked) s.inLibrary ?: now else s.inLibrary
                )
            )
            YouTube.likeVideo(s.id, liked)
        }
    }

    fun subscribeChannel(channelId: String, subscribe: Boolean) {
        scope.launch {
            try {
                YouTube.subscribeChannel(channelId, subscribe)
                val artist = database.artistEntity(channelId)
                if (artist != null) {
                    val now = if (subscribe) Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) else null
                    database.update(artist.copy(bookmarkedAt = now))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to subscribe/unsubscribe channel $channelId")
            }
        }
    }

    private fun nowLocal() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    fun savePodcast(podcastId: String, save: Boolean) {
        scope.launch {
            YouTube.savePodcast(podcastId, save).onFailure { Timber.e(it, "Failed to save podcast $podcastId") }
            database.podcast(podcastId).firstOrNull()?.let { podcast ->
                database.update(podcast.copy(bookmarkedAt = if (save) nowLocal() else null))
            }
        }
    }

    fun saveEpisode(episodeId: String, save: Boolean, setVideoId: String? = null) {
        scope.launch {
            val result = if (save) {
                YouTube.addEpisodeToSavedEpisodes(episodeId)
            } else if (!setVideoId.isNullOrBlank()) {
                YouTube.removeEpisodeFromSavedEpisodes(episodeId, setVideoId)
            } else {
                Result.success(Unit)
            }
            result.onFailure { Timber.e(it, "Failed to update saved episode $episodeId") }
            database.getSongById(episodeId)?.let { song ->
                database.update(song.song.copy(inLibrary = if (save) nowLocal() else null, isEpisode = true))
            }
        }
    }

    fun syncPodcastSubscriptions() {
        scope.launch {
            YouTube.savedPodcastShows().onSuccess { shows ->
                val remoteIds = shows.map { it.id }.toSet()
                val now = nowLocal()
                shows.forEach { show ->
                    val existing = database.podcast(show.id).firstOrNull()
                    if (existing == null) {
                        database.insert(
                            com.metrolist.music.db.entities.PodcastEntity(
                                id = show.id,
                                title = show.title,
                                author = show.author?.name,
                                thumbnailUrl = show.thumbnail,
                                channelId = show.channelId,
                                bookmarkedAt = now,
                            ),
                        )
                    } else if (existing.bookmarkedAt == null) {
                        database.update(existing.copy(bookmarkedAt = now))
                    }
                }
                database.subscribedPodcasts().firstOrNull().orEmpty()
                    .filter { it.id !in remoteIds }
                    .forEach { database.update(it.copy(bookmarkedAt = null)) }
            }.onFailure { Timber.e(it, "Failed to sync podcast subscriptions") }
        }
    }

    fun syncEpisodesForLater() {
        scope.launch {
            YouTube.episodesForLater().onSuccess { episodes ->
                val remoteIds = episodes.map { it.id }.toSet()
                val now = nowLocal()
                episodes.forEach { item ->
                    val existing = database.getSongById(item.id)
                    if (existing == null) {
                        database.insert(item.toMediaMetadata())
                    }
                    database.getSongById(item.id)?.let { song ->
                        if (song.song.inLibrary == null || !song.song.isEpisode) {
                            database.update(song.song.copy(inLibrary = song.song.inLibrary ?: now, isEpisode = true))
                        }
                    }
                }
                database.savedEpisodeEntitiesByCreateDateAsc()
                    .filter { it.id !in remoteIds }
                    .forEach { database.update(it.copy(inLibrary = null)) }
            }.onFailure { Timber.e(it, "Failed to sync episodes for later") }
        }
    }

    fun clearPodcastData() {
        scope.launch {
            database.subscribedPodcasts().firstOrNull().orEmpty().forEach { database.update(it.copy(bookmarkedAt = null)) }
            database.savedEpisodeEntitiesByCreateDateAsc().forEach { database.update(it.copy(inLibrary = null)) }
        }
    }

    fun createPlaylist(playlist: PlaylistEntity, syncWithYouTube: Boolean, callback: ((String, Boolean) -> Unit)? = null) {
        scope.launch {
            if (syncWithYouTube) {
                try {
                    val id = YouTube.createPlaylist(playlist.name)
                    if (id != null) {
                        val entity = playlist.copy(id = id, browseId = id)
                        database.insert(entity)
                        callback?.invoke(id, true)
                    } else {
                        database.insert(playlist)
                        callback?.invoke(playlist.id, false)
                    }
                } catch (e: Exception) {
                    database.insert(playlist)
                    callback?.invoke(playlist.id, false)
                }
            } else {
                database.insert(playlist)
                callback?.invoke(playlist.id, true)
            }
        }
    }

    fun scheduleAddToPlaylist(browseId: String, playlistId: String) {}
    fun scheduleAddToPlaylist(browseId: String, playlistId: String, songIds: List<String>) {
        scope.launch {
            songIds.forEach { songId ->
                YouTube.addToPlaylist(playlistId, songId)
            }
        }
    }

    fun scheduleRemoveFromPlaylist(
        browseId: String,
        songId: String,
        playlistId: String? = null,
        setVideoIdProvider: (() -> String?)? = null
    ) {
        scope.launch {
            playlistId?.let { YouTube.removeFromPlaylist(it, songId, setVideoIdProvider?.invoke() ?: "") }
        }
    }

    fun scheduleRemoveFromPlaylist(
        browseId: String,
        playlistId: String,
        songIds: List<String>,
        setVideoIdProvider: (() -> String?)? = null
    ) {
        scope.launch {
            val setVideoId = setVideoIdProvider?.invoke() ?: ""
            songIds.forEach { songId ->
                YouTube.removeFromPlaylist(playlistId, songId, setVideoId)
            }
        }
    }
}
