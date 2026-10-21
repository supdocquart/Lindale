package com.metrolist.music.lyrics

import com.metrolist.kugou.KuGou
import com.metrolist.music.PlatformContext
import com.metrolist.music.constants.EnableKugouKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get

object KuGouLyricsProvider : LyricsProvider {
    override val name = "KuGou"

    override fun isEnabled(context: PlatformContext): Boolean = context.dataStore[EnableKugouKey] ?: true

    override suspend fun getLyrics(
        context: PlatformContext,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = KuGou.getLyrics(title, artist, duration, album)
}
