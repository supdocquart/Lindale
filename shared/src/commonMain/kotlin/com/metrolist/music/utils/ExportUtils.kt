package com.metrolist.music.utils

import android.net.Uri
import com.metrolist.innertube.models.SongItem
import java.io.File

fun getExportFileUri(context: Any?, file: File): Uri = Uri.fromFile(file)

/** Copies an exported file to Documents\Metrolist and reports where it went. */
fun saveToPublicDocuments(context: Any?, file: File, mimeType: String? = null): Result<Unit> = runCatching {
    val documents = File(System.getProperty("user.home"), "Documents").takeIf { it.isDirectory }
        ?: File(System.getProperty("user.home"))
    val dir = File(documents, "Metrolist").apply { mkdirs() }
    val target = File(dir, file.name)
    file.copyTo(target, overwrite = true)
    ToastHost.post(target.absolutePath, Toast.LENGTH_LONG)
}

private fun List<Any?>.toRows(): List<PlaylistExporter.Row> = mapNotNull { item ->
    when (item) {
        is SongItem -> PlaylistExporter.Row(
            id = item.id,
            title = item.title,
            artists = item.artists.joinToString(", ") { it.name },
            album = item.album?.name,
            durationSec = item.duration ?: 0,
        )
        is com.metrolist.music.db.entities.Song -> PlaylistExporter.Row(
            id = item.id,
            title = item.song.title,
            artists = item.artists.joinToString(", ") { it.name },
            album = item.album?.title,
            durationSec = item.song.duration,
        )
        is com.metrolist.music.models.MediaMetadata -> PlaylistExporter.Row(
            id = item.id,
            title = item.title,
            artists = item.artists.joinToString(", ") { it.name },
            album = item.album?.title,
            durationSec = item.duration,
        )
        else -> null
    }
}

fun exportYouTubePlaylistAsCSV(
    context: Any?,
    playlistTitle: String,
    songs: List<Any?>,
): Result<File> = PlaylistExporter.writeCsv(playlistTitle, songs.toRows())

fun exportYouTubePlaylistAsM3U(
    context: Any?,
    playlistTitle: String,
    songs: List<Any?>,
): Result<File> = PlaylistExporter.writeM3u(playlistTitle, songs.toRows())
