package com.metrolist.music.viewmodels
import com.metrolist.music.platform.AppContext

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.AlbumWithSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModel(
    val context: AppContext,
    val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _albumId = MutableStateFlow(normalizeId(savedStateHandle.get<String>("albumId") ?: ""))
    val albumId: StateFlow<String> = _albumId.asStateFlow()
    val playlistId = MutableStateFlow("")
    val albumWithSongs: StateFlow<AlbumWithSongs?> = _albumId
        .flatMapLatest { id ->
            if (id.isBlank()) flowOf(null) else database.albumWithSongs(id)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    var otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())

    companion object {
        fun normalizeId(id: String): String {
            if (id.isBlank()) return ""
            return if (!id.startsWith("MPRE") && !id.startsWith("OLAK") && !id.startsWith("FEmusic") && !id.startsWith("VL")) {
                "MPREb_$id"
            } else {
                id
            }
        }
    }

    init {
        viewModelScope.launch {
            albumWithSongs.collect { album ->
                if (album != null && playlistId.value.isBlank()) {
                    playlistId.value = album.album.playlistId ?: ""
                }
            }
        }
        if (_albumId.value.isNotBlank()) {
            fetchAlbum(_albumId.value)
        }
    }

    fun loadAlbum(id: String) {
        val norm = normalizeId(id)
        if (norm.isNotBlank() && _albumId.value != norm) {
            _albumId.value = norm
            fetchAlbum(norm)
        } else if (norm.isNotBlank() && albumWithSongs.value == null) {
            fetchAlbum(norm)
        }
    }

    private fun fetchAlbum(id: String) {
        val browseId = normalizeId(id)
        if (browseId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            Timber.d("Fetching album: id=$id, browseId=$browseId")
            var result = YouTube.album(browseId)
            if (result.isFailure && browseId.startsWith("MPREb_")) {
                val rawId = browseId.removePrefix("MPREb_")
                Timber.d("Retrying album fetch with raw id: $rawId")
                result = YouTube.album(rawId)
            }
            if (result.isFailure && !browseId.startsWith("VL") && browseId.startsWith("OLAK")) {
                Timber.d("Retrying album fetch as playlist: VL$browseId")
                result = YouTube.album("VL$browseId")
            }
            result.onSuccess { albumPage ->
                playlistId.value = albumPage.album.playlistId ?: ""
                otherVersions.value = albumPage.otherVersions
                database.insert(albumPage)
                Timber.d("Fetched and inserted album: ${albumPage.album.title}")
            }.onFailure { error ->
                Timber.e(error, "Failed to load album $id (browseId=$browseId) from YouTube")
            }
        }
    }
}
