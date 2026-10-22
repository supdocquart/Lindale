package com.metrolist.music.lyrics
import com.metrolist.music.platform.AppContext

import com.metrolist.kugou.KuGou
import com.metrolist.music.constants.EnableKugouKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get

object KuGouLyricsProvider : LyricsProvider {
    override val name = "KuGou"

    override fun isEnabled(context: AppContext): Boolean = context.dataStore[EnableKugouKey] ?: true

    override suspend fun getLyrics(
        context: AppContext,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = KuGou.getLyrics(title, artist, duration, album)
}
