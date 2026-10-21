package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import com.metrolist.music.PlatformContext
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CachePlaylistViewModel(val context: PlatformContext, val database: MusicDatabase) : ViewModel() {
    val showCachePlaylistDialog = MutableStateFlow(false)
    val cachePlaylistTitle = MutableStateFlow("")
    val cachedSongs: StateFlow<List<Song>> = MutableStateFlow<List<Song>>(emptyList()).asStateFlow()

    fun checkCache(any: Any) {}
    fun removeCache(any: Any) {}
    fun removeSongFromCache(songId: String) {}
    fun removeSongsFromCache(songIds: List<String>) {}
}
