package dagger.hilt.android

import com.metrolist.music.di.LyricsHelperEntryPoint
import com.metrolist.music.lyrics.LyricsHelper

object EntryPointAccessors {
    @Suppress("UNCHECKED_CAST")
    fun <T> fromApplication(context: Any?, entryPoint: Class<T>): T {
        return object : LyricsHelperEntryPoint {
            override fun lyricsHelper(): LyricsHelper =
                LyricsHelper(context as? com.metrolist.music.PlatformContext ?: android.content.Context())
        } as T
    }
}
