/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.extensions
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import com.metrolist.innertube.models.dropExplicitDuplicates
import com.metrolist.innertube.models.normalizeTitleKey
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.Song

fun <T> List<T>.reversed(reversed: Boolean) = if (reversed) asReversed() else this

fun <T> MutableList<T>.move(
    fromIndex: Int,
    toIndex: Int,
): MutableList<T> {
    add(toIndex, removeAt(fromIndex))
    return this
}

// Extension function to filter explicit content for local Song entities
fun List<Song>.filterExplicit(enabled: Boolean = true) =
    // DESKTOP: hide explicit twins when a clean release of the same song is in the list.
    (if (enabled) filter { !it.song.explicit } else this)
        .dropExplicitDuplicates(
            isExplicit = { it.song.explicit },
            key = { song ->
                val base = normalizeTitleKey(song.song.title, song.artists.map { it.name })
                if (song.song.duration > 0) "$base|${song.song.duration / 3}" else base
            },
        )

// Extension function to filter video songs for local Song entities
fun List<Song>.filterVideoSongs(enabled: Boolean = true) =
    if (enabled) {
        filter { !it.song.isVideo }
    } else {
        this
    }

// Extension function to filter explicit content for local Album entities
fun List<Album>.filterExplicitAlbums(enabled: Boolean = true) =
    // DESKTOP: hide explicit twins when a clean release of the same album is in the list.
    (if (enabled) filter { !it.album.explicit } else this)
        .dropExplicitDuplicates(
            isExplicit = { it.album.explicit },
            key = { album -> normalizeTitleKey(album.album.title, album.artists.map { it.name }) },
        )

// Extension function to filter YouTube Shorts playlist
fun List<Playlist>.filterYoutubeShorts(enabled: Boolean = false) =
    if (enabled) {
        filterNot { it.playlist.browseId?.startsWith("SS") == true }
    } else {
        this
    }
