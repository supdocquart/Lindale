/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import com.metrolist.music.PlatformContext
import com.metrolist.music.betterlyrics.BetterLyrics
import com.metrolist.music.constants.EnableBetterLyricsKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: PlatformContext): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        context: PlatformContext,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching { BetterLyrics.getLyrics(title, artist, duration.toLong()) ?: throw Exception("No lyrics") }
}



