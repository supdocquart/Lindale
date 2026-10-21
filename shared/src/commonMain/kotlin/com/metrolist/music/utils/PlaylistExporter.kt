package com.metrolist.music.utils

import com.metrolist.music.PlatformContext
import com.metrolist.music.db.entities.PlaylistSong
import java.io.File

/** Writes playlists as CSV or extended M3U into the app's export folder. */
object PlaylistExporter {
    internal data class Row(val id: String, val title: String, val artists: String, val album: String?, val durationSec: Int)

    fun exportPlaylistAsCSV(
        context: PlatformContext,
        playlistName: String,
        songs: List<PlaylistSong>,
    ): Result<File> = writeCsv(playlistName, songs.map { it.toRow() })

    fun exportPlaylistAsM3U(
        context: PlatformContext,
        playlistName: String,
        songs: List<PlaylistSong>,
    ): Result<File> = writeM3u(playlistName, songs.map { it.toRow() })

    private fun PlaylistSong.toRow() = Row(
        id = song.id,
        title = song.song.title,
        artists = song.artists.joinToString(", ") { it.name },
        album = song.album?.title,
        durationSec = song.song.duration,
    )

    internal fun exportDir(): File =
        File(android.content.Context().cacheDir, "exports").apply { mkdirs() }

    internal fun safeName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "playlist" }.take(80)

    private fun csvField(value: String?): String {
        val v = value.orEmpty()
        return if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    internal fun writeCsv(playlistName: String, rows: List<Row>): Result<File> = runCatching {
        val file = File(exportDir(), "${safeName(playlistName)}.csv")
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿Title,Artist,Album,Duration,URL\n")
            rows.forEach { r ->
                w.write(listOf(r.title, r.artists, r.album, r.durationSec.takeIf { it > 0 }?.toString(), "https://music.youtube.com/watch?v=${r.id}")
                    .joinToString(",") { csvField(it) })
                w.write("\n")
            }
        }
        file
    }

    internal fun writeM3u(playlistName: String, rows: List<Row>): Result<File> = runCatching {
        val file = File(exportDir(), "${safeName(playlistName)}.m3u")
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("#EXTM3U\n")
            w.write("#PLAYLIST:$playlistName\n")
            rows.forEach { r ->
                w.write("#EXTINF:${if (r.durationSec > 0) r.durationSec else -1},${r.artists} - ${r.title}\n")
                w.write("https://music.youtube.com/watch?v=${r.id}\n")
            }
        }
        file
    }
}
