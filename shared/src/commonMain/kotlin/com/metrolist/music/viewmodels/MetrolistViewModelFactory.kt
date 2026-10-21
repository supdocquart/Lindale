package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import com.metrolist.music.PlatformContext
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.lyrics.LyricsHelper
import com.metrolist.music.ui.component.DownloadUtil
import com.metrolist.music.ui.screens.equalizer.EQViewModel
import com.metrolist.music.ui.screens.equalizer.wizard.WizardViewModel
import com.metrolist.music.ui.screens.wrapped.WrappedManager
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.SyncUtils
import kotlin.reflect.KClass

class MetrolistViewModelFactory(
    private val database: MusicDatabase,
    private val context: PlatformContext = PlatformContext(),
    private val syncUtils: SyncUtils = SyncUtils(context, database),
    private val downloadUtil: DownloadUtil = DownloadUtil(),
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
        val navEntry = extras[androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY] as? androidx.navigation.NavBackStackEntry
        val savedStateHandle = navEntry?.savedStateHandle ?: try {
            extras.createSavedStateHandle()
        } catch (_: Exception) {
            SavedStateHandle()
        }

        val vm: ViewModel = when (modelClass) {
            HomeViewModel::class -> HomeViewModel(
                context = context,
                database = database,
                syncUtils = syncUtils,
                wrappedManager = WrappedManager(database, context),
                networkConnectivity = NetworkConnectivityObserver(context)
            )
            AccountSettingsViewModel::class -> AccountSettingsViewModel(context = context)
            AccountViewModel::class -> AccountViewModel(context = context, database = database)
            AlbumViewModel::class -> AlbumViewModel(context = context, database = database, savedStateHandle = savedStateHandle)
            ArtistAlbumsViewModel::class -> ArtistAlbumsViewModel(database = database, savedStateHandle = savedStateHandle)
            ArtistItemsViewModel::class -> ArtistItemsViewModel(context = context, savedStateHandle = savedStateHandle)
            ArtistViewModel::class -> ArtistViewModel(context = context, database = database, syncUtils = syncUtils, savedStateHandle = savedStateHandle)
            ArtistSongsViewModel::class -> ArtistSongsViewModel(context = context, database = database, savedStateHandle = savedStateHandle)
            AutoPlaylistViewModel::class -> AutoPlaylistViewModel(context = context, database = database, savedStateHandle = savedStateHandle, syncUtils = syncUtils)
            BackupRestoreViewModel::class -> BackupRestoreViewModel(database = database)
            BrowseViewModel::class -> BrowseViewModel(savedStateHandle = savedStateHandle)
            CachePlaylistViewModel::class -> CachePlaylistViewModel(context = context, database = database)
            ChartsViewModel::class -> ChartsViewModel()
            HistoryViewModel::class -> HistoryViewModel(context = context, database = database)
            LibrarySongsViewModel::class -> LibrarySongsViewModel(context = context, database = database, downloadUtil = downloadUtil, syncUtils = syncUtils)
            LibraryArtistsViewModel::class -> LibraryArtistsViewModel(context = context, database = database, syncUtils = syncUtils)
            LibraryAlbumsViewModel::class -> LibraryAlbumsViewModel(context = context, database = database, syncUtils = syncUtils)
            LibraryPlaylistsViewModel::class -> LibraryPlaylistsViewModel(context = context, database = database, syncUtils = syncUtils)
            LibraryMixViewModel::class -> LibraryMixViewModel(context = context, database = database, syncUtils = syncUtils)
            LibraryPodcastsViewModel::class -> LibraryPodcastsViewModel(context = context, database = database, syncUtils = syncUtils)
            LocalPlaylistViewModel::class -> LocalPlaylistViewModel(context = context, database = database, savedStateHandle = savedStateHandle)
            LocalSearchViewModel::class -> LocalSearchViewModel(context = context, database = database)
            LyricsMenuViewModel::class -> LyricsMenuViewModel(lyricsHelper = LyricsHelper(context), database = database, context = context)
            LyricsViewModel::class -> LyricsViewModel()
            MoodAndGenresViewModel::class -> MoodAndGenresViewModel()
            NewReleaseViewModel::class -> NewReleaseViewModel(context = context, database = database)
            OnlinePlaylistViewModel::class -> OnlinePlaylistViewModel(context = context, savedStateHandle = savedStateHandle, database = database)
            OnlinePodcastViewModel::class -> OnlinePodcastViewModel(savedStateHandle = savedStateHandle, database = database, syncUtils = syncUtils)
            OnlineSearchSuggestionViewModel::class -> OnlineSearchSuggestionViewModel(context = context, database = database)
            OnlineSearchViewModel::class -> OnlineSearchViewModel(context = context, savedStateHandle = savedStateHandle)
            PlaylistsViewModel::class -> PlaylistsViewModel(context = context, database = database, syncUtils = syncUtils)
            StatsViewModel::class -> StatsViewModel(context = context, database = database)
            TopPlaylistViewModel::class -> TopPlaylistViewModel(context = context, database = database, savedStateHandle = savedStateHandle)
            YouTubeBrowseViewModel::class -> YouTubeBrowseViewModel(context = context, savedStateHandle = savedStateHandle)
            EQViewModel::class -> EQViewModel()
            WizardViewModel::class -> WizardViewModel()
            else -> {
                try {
                    modelClass.java.getDeclaredConstructor().newInstance()
                } catch (e: Exception) {
                    error("Cannot construct ViewModel ${modelClass.simpleName}: ${e.message}")
                }
            }
        }
        return vm as T
    }
}
