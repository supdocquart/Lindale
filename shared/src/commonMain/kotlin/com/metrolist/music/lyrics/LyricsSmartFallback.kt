// DESKTOP: runs only after the upstream LyricsHelper has already queried every provider with the
// song's own metadata and found nothing. This retries the text providers with cleaner title/artist
// guesses, in parallel, to rescue songs whose YouTube upload titles confuse the lyrics sites.
package com.metrolist.music.lyrics

import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.platform.AppContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object LyricsSmartFallback {
    suspend fun find(
        context: AppContext,
        providers: List<LyricsProvider>,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): LyricsWithProvider? {
        // Ask all providers at once; prefer the earliest one in the user's order, but don't wait
        // more than a few seconds on slower, higher-priority providers once something was found.
        suspend fun tryAll(list: List<LyricsProvider>, t: String, a: String, d: Int, al: String?): LyricsWithProvider? =
            coroutineScope {
                val n = list.size
                val results = java.util.concurrent.atomic.AtomicReferenceArray<String?>(n)
                val done = java.util.concurrent.atomic.AtomicIntegerArray(n)
                val jobs = list.mapIndexed { i, provider ->
                    launch {
                        val text = runCatching {
                            withTimeoutOrNull(20_000) {
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
                    delay(50)
                }
                jobs.forEach { it.cancel() }
                picked?.let { i ->
                    if (t != title || a != artist || d != duration) {
                        timber.log.Timber.i("lyrics: %s matched with title='%s' artist='%s' duration=%d", list[i].name, t, a, d)
                    }
                    LyricsWithProvider(results.get(i)!!, list[i].name)
                }
            }

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
}
