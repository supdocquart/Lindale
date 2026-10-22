package com.metrolist.music.lyrics
import com.metrolist.music.platform.AppContext
import kotlinx.coroutines.launch

import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.models.MediaMetadata

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)

class LyricsHelper(private val context: AppContext) {
    private fun providerOrder(): String =
        runCatching { context.dataStore[LyricsProviderOrderKey] }.getOrNull() ?: ""

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        val artist = mediaMetadata.artists.joinToString(", ") { it.name }
        return find(mediaMetadata.id, mediaMetadata.title, artist, mediaMetadata.duration, mediaMetadata.album?.title)
            ?: LyricsWithProvider(LyricsEntity.LYRICS_NOT_FOUND, "Unknown")
    }

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> =
        find(id, title, artist, duration, album)?.let { Result.success(it.lyrics) }
            ?: Result.failure(NoSuchElementException("Lyrics not found"))

    /**
     * DESKTOP: three passes so more songs get lyrics:
     * 1. every provider with the song's own metadata (upstream behaviour),
     * 2. text providers with cleaned-up title/artist guesses (see [LyricsQuery]),
     * 3. the same guesses ignoring duration, for uploads with intros/outros.
     * Providers that look songs up by video id are only asked once.
     */
    private suspend fun find(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): LyricsWithProvider? {
        val providers = LyricsProviderRegistry.getOrderedProviders(providerOrder())
            .filter { runCatching { it.isEnabled(context) }.getOrDefault(true) }

        // Ask all providers at once; prefer the earliest one in the user's order, but don't wait
        // more than a few seconds on slower, higher-priority providers once something was found.
        suspend fun tryAll(list: List<LyricsProvider>, t: String, a: String, d: Int, al: String?): LyricsWithProvider? =
            kotlinx.coroutines.coroutineScope {
                val n = list.size
                val results = java.util.concurrent.atomic.AtomicReferenceArray<String?>(n)
                val done = java.util.concurrent.atomic.AtomicIntegerArray(n)
                val jobs = list.mapIndexed { i, provider ->
                    launch {
                        val text = runCatching {
                            kotlinx.coroutines.withTimeoutOrNull(20_000) {
                                provider.getLyrics(context, id, t, a, d, al).getOrNull()
                            }
                        }.getOrNull()
                        if (!text.isNullOrBlank() && text != LyricsEntity.LYRICS_NOT_FOUND) results.set(i, text)
                        done.set(i, 1)
                    }
                }
                var firstHitAt = 0L
                var picked: Int? = null
                while (picked == null) {
                    var allBeforeDone = true
                    for (i in 0 until n) {
                        if (results.get(i) != null && allBeforeDone) { picked = i; break }
                        if (done.get(i) == 0) allBeforeDone = false
                    }
                    if (picked != null) break
                    val firstHit = (0 until n).firstOrNull { results.get(it) != null }
                    if (firstHit != null) {
                        if (firstHitAt == 0L) firstHitAt = System.currentTimeMillis()
                        if (System.currentTimeMillis() - firstHitAt > 3_000) { picked = firstHit; break }
                    }
                    if ((0 until n).all { done.get(it) == 1 }) break
                    kotlinx.coroutines.delay(50)
                }
                jobs.forEach { it.cancel() }
                picked?.let { i ->
                    if (t != title || a != artist || d != duration) {
                        timber.log.Timber.i("lyrics: %s matched with title='%s' artist='%s' duration=%d", list[i].name, t, a, d)
                    }
                    LyricsWithProvider(results.get(i)!!, list[i].name)
                }
            }

        tryAll(providers, title, artist, duration, album)?.let { return it }

        val byText = providers.filterNot { it is YouTubeLyricsProvider || it is YouTubeSubtitleLyricsProvider }
        val guesses = LyricsQuery.variants(title, artist).drop(1)
        for ((t, a) in guesses) {
            tryAll(byText, t, a, duration, null)?.let { return it }
        }
        if (duration > 0) {
            for ((t, a) in guesses.take(3)) {
                tryAll(byText, t, a, -1, null)?.let { return it }
            }
        }
        timber.log.Timber.i("lyrics: none found for '%s' / '%s' (tried %d guesses)", title, artist, guesses.size + 1)
        return null
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
