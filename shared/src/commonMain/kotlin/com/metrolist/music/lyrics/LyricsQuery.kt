package com.metrolist.music.lyrics

/**
 * DESKTOP: builds cleaner title/artist guesses for songs whose YouTube metadata confuses the
 * lyrics sites — user uploads like "Eminem - Evil [Official Audio]" by a fan channel,
 * "Song (feat. X) [Lyric Video]", "Song Lyrics", "Artist / Song".
 */
internal object LyricsQuery {
    private const val JUNK_WORDS =
        "official|audio|video|lyric|lyrics|visuali[sz]er|music video|mv|hd|hq|4k|8d|" +
            "remaster(ed)?|explicit|clean|dirty|uncensored|version|edit|extended|" +
            "full song|color coded|letra|sözleri|şarkı sözleri|subtitulad[oa]|traduç[aã]o|prod\\.?.*"

    // "(Official Video)", "[Lyrics]", "【MV】", "(HD 2010)" ...
    private val bracketJunk =
        Regex("""\s*[(\[【{]([^)\]】}]*\b($JUNK_WORDS)\b[^)\]】}]*)[)\]】}]""", RegexOption.IGNORE_CASE)

    // "... - Official Video", "... | Lyrics", "... Lyrics" at the very end
    private val trailingJunk =
        Regex("""\s*(?:[-|–—:]\s*)?(?:\b(?:official|lyrics?|audio|video|visuali[sz]er)\b\s*)+$""", RegexOption.IGNORE_CASE)

    private val feat =
        Regex("""\s*[(\[]?\s*\b(feat\.?|ft\.?|featuring)\s+.*?(?=\s[-–—|]\s|[)\]]|$)[)\]]?""", RegexOption.IGNORE_CASE)

    private val separators = listOf(" - ", " – ", " — ", " / ", " | ", " ~ ")

    fun clean(title: String): String {
        var t = title
        repeat(3) { t = t.replace(bracketJunk, "") }
        t = t.replace(trailingJunk, "")
        return t.replace(Regex("\\s{2,}"), " ").trim().trim('-', '|', '–', '—', ':', ' ')
    }

    fun withoutFeat(text: String): String = text.replace(feat, "").trim()

    fun primaryArtist(artist: String): String =
        artist.split(",", "&", " x ", " X ", " and ", " ve ", ";").first().let(::withoutFeat).trim()

    /** Ordered (title, artist) guesses, original first, without duplicates. */
    fun variants(title: String, artist: String): List<Pair<String, String>> {
        val out = LinkedHashSet<Pair<String, String>>()
        out += title to artist
        val cleaned = clean(title)
        out += cleaned to artist

        // "Artist - Title" style uploads: the channel name is usually not the artist.
        separators.firstOrNull { cleaned.contains(it) }?.let { sep ->
            val left = cleaned.substringBefore(sep).trim()
            val right = clean(cleaned.substringAfter(sep).trim())
            if (left.isNotBlank() && right.isNotBlank()) {
                out += right to left
                out += withoutFeat(right) to primaryArtist(left)
                out += right to artist
            }
        }
        out += withoutFeat(cleaned) to primaryArtist(artist)
        return out.filter { it.first.isNotBlank() && it.second.isNotBlank() }
    }
}
