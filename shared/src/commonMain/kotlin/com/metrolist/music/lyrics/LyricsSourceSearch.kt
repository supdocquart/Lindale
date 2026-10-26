// DESKTOP: backs the "Change lyrics source" dialog. Every enabled provider is asked in parallel, with the
// cleaned title first and then the same title/artist guesses the automatic fallback uses, so the user sees
// which sources actually have lyrics for this song instead of trying them one by one.
package com.metrolist.music.lyrics

import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.platform.AppContext
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

object LyricsSourceSearch {
    sealed interface Status {
        data object Searching : Status
        data class Found(val lyrics: String, val synced: Boolean, val preview: String) : Status
        data object NotFound : Status
    }

    private const val PER_ATTEMPT_TIMEOUT_MS = 10_000L
    private const val PER_PROVIDER_TIMEOUT_MS = 30_000L
    private val SYNCED_LINE = Regex("""^\s*\[\d{1,2}:\d{2}""", RegexOption.MULTILINE)
    private val LEADING_TAGS = Regex("""^\s*(\[[^\]]*\]\s*)+""")

    /** Enabled providers in the user's order (same rules as LyricsHelper). */
    suspend fun enabledProviders(context: AppContext): List<LyricsProvider> {
        val order = context.dataStore.data.first()[LyricsProviderOrderKey].orEmpty()
        val all = if (order.isNotBlank()) {
            LyricsProviderRegistry.getOrderedProviders(order)
        } else {
            LyricsProviderRegistry.getDefaultProviderOrder().mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
        }
        return all.filter { runCatching { it.isEnabled(context) }.getOrDefault(false) }
    }

    suspend fun search(context: AppContext, provider: LyricsProvider, meta: MediaMetadata): Status =
        withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
            val artist = meta.artists.joinToString { it.name }
            val attempts = buildList {
                add(LyricsUtils.cleanTitleForSearch(meta.title) to artist)
                addAll(LyricsQuery.variants(meta.title, artist).take(4))
            }.distinct()
            for ((title, artists) in attempts) {
                val text = withTimeoutOrNull(PER_ATTEMPT_TIMEOUT_MS) {
                    runCatching {
                        provider.getLyrics(context, meta.id, title, artists, meta.duration, meta.album?.title).getOrNull()
                    }.getOrNull()
                }
                if (!text.isNullOrBlank() && text != LyricsEntity.LYRICS_NOT_FOUND) {
                    val filtered = LyricsUtils.filterLyricsCreditLines(text)
                    return@withTimeoutOrNull Status.Found(filtered, isSynced(filtered), preview(filtered))
                }
            }
            Status.NotFound
        } ?: Status.NotFound

    fun isSynced(lyrics: String): Boolean = SYNCED_LINE.containsMatchIn(lyrics)

    private fun preview(lyrics: String): String =
        lyrics.lineSequence()
            .map { it.replace(LEADING_TAGS, "").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("[") && !it.startsWith("{") }
            .take(2)
            .joinToString("  /  ")
}
