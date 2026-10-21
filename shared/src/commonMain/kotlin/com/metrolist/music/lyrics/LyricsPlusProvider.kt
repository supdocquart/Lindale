package com.metrolist.music.lyrics
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import com.metrolist.music.PlatformContext
object LyricsPlusProvider : LyricsProvider {
    override val name = "LyricsPlus"
    override fun isEnabled(context: PlatformContext) = false
    override suspend fun getLyrics(
        context: PlatformContext,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?
    ): Result<String> = Result.failure(Exception("Not implemented"))
}

