package com.metrolist.music.viewmodels
import com.metrolist.music.platform.AppContext

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.LyricsHelper
import com.metrolist.music.lyrics.LyricsProviderRegistry
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class LyricsSearchResult(
    val lyrics: String = "",
    val providerName: String = "",
)

class LyricsMenuViewModel(
    private val lyricsHelper: LyricsHelper? = null,
    private val database: MusicDatabase? = null,
    private val context: AppContext = AppContext(),
) : ViewModel() {
    val isNetworkAvailable: StateFlow<Boolean> = MutableStateFlow(true)

    private val _results = MutableStateFlow<List<LyricsSearchResult>>(emptyList())
    val results: StateFlow<List<LyricsSearchResult>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var searchJob: Job? = null

    /** Queries every enabled provider and lists each result so the user can pick one. */
    fun search(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ) {
        searchJob?.cancel()
        _results.value = emptyList()
        _isLoading.value = true
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val providers = LyricsProviderRegistry.getOrderedProviders("")
                .filter { runCatching { it.isEnabled(context) }.getOrDefault(true) }
            val jobs = providers.map { provider ->
                launch {
                    withTimeoutOrNull(20_000) {
                        runCatching {
                            provider.getAllLyrics(context, id, title, artist, duration, album) { lyrics ->
                                if (lyrics.isNotBlank()) {
                                    _results.update { it + LyricsSearchResult(lyrics, provider.name) }
                                }
                            }
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
            _isLoading.value = false
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        _isLoading.value = false
    }

    /** Drops the cached lyrics row and fetches it again through the normal provider chain. */
    fun refetchLyrics(mediaMetadata: Any?, lyrics: Any? = null) {
        val meta = mediaMetadata as? MediaMetadata ?: return
        val helper = lyricsHelper ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val fetched = runCatching { helper.getLyrics(meta) }.getOrNull() ?: return@launch
            database?.query {
                upsert(LyricsEntity(meta.id, fetched.lyrics, fetched.provider))
            }
        }
    }
}
