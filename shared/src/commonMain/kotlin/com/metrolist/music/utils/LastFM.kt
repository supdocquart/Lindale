package com.metrolist.music.utils
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

object LastFM {
    suspend fun scrobble(artist: String, track: String, duration: Int, timestamp: Long, album: String?) {}
    suspend fun updateNowPlaying(artist: String, track: String, album: String?, duration: Int) {}
}
