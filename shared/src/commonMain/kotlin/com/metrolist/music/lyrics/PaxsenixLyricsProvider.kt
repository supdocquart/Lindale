package com.metrolist.music.lyrics
import com.metrolist.music.platform.AppContext

import com.metrolist.music.constants.EnablePaxsenixKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.paxsenix.Paxsenix

object PaxsenixLyricsProvider : LyricsProvider {
    override val name = "Paxsenix"

    override fun isEnabled(context: AppContext): Boolean = context.dataStore[EnablePaxsenixKey] ?: true

    override suspend fun getLyrics(
        context: AppContext,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Paxsenix.getLyrics(title, artist, duration, album)
}
