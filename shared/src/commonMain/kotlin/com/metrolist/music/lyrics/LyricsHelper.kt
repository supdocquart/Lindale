package com.metrolist.music.lyrics

import com.metrolist.music.PlatformContext
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.models.MediaMetadata

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)

class LyricsHelper(private val context: PlatformContext) {
    private fun providerOrder(): String =
        runCatching { context.dataStore[LyricsProviderOrderKey] }.getOrNull() ?: ""

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        val artist = mediaMetadata.artists.joinToString(", ") { it.name }
        val providers = LyricsProviderRegistry.getOrderedProviders(providerOrder())
        for (provider in providers) {
            if (!runCatching { provider.isEnabled(context) }.getOrDefault(true)) continue
            val result = runCatching {
                provider.getLyrics(
                    context = context,
                    id = mediaMetadata.id,
                    title = mediaMetadata.title,
                    artist = artist,
                    duration = mediaMetadata.duration,
                    album = mediaMetadata.album?.title,
                ).getOrNull()
            }.getOrNull()
            if (!result.isNullOrBlank()) {
                return LyricsWithProvider(result, provider.name)
            }
        }
        return LyricsWithProvider(LyricsEntity.LYRICS_NOT_FOUND, "Unknown")
    }

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> {
        val providers = LyricsProviderRegistry.getOrderedProviders(providerOrder())
        for (provider in providers) {
            if (!runCatching { provider.isEnabled(context) }.getOrDefault(true)) continue
            val res = runCatching {
                provider.getLyrics(context, id, title, artist, duration, album).getOrNull()
            }.getOrNull()
            if (!res.isNullOrBlank()) {
                return Result.success(res)
            }
        }
        return Result.failure(NoSuchElementException("Lyrics not found"))
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<List<String>> {
        val found = mutableListOf<String>()
        for (provider in LyricsProviderRegistry.getOrderedProviders(providerOrder())) {
            val res = runCatching {
                provider.getLyrics(context, "", title, artist, duration, album).getOrNull()
            }.getOrNull()
            if (!res.isNullOrBlank()) {
                found.add(res)
            }
        }
        return Result.success(found)
    }
}
