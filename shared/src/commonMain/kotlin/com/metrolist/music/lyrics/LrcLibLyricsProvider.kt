package com.metrolist.music.lyrics
import com.metrolist.music.platform.AppContext

import com.metrolist.lrclib.LrcLib
import com.metrolist.music.constants.EnableLrcLibKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get

object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override fun isEnabled(context: AppContext): Boolean = context.dataStore[EnableLrcLibKey] ?: true

    override suspend fun getLyrics(
        context: AppContext,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = LrcLib.getLyrics(title, artist, duration, album)
}
