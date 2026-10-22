package com.metrolist.music.lyrics
import com.metrolist.music.platform.AppContext
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
object ZemerLyricsProvider : LyricsProvider {
    override val name = "Zemer"
    override fun isEnabled(context: AppContext) = false
    override suspend fun getLyrics(
        context: AppContext,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?
    ): Result<String> = Result.failure(Exception("Not implemented"))
}
