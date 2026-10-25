package com.metrolist.innertube.models

import timber.log.Timber

/**
 * DESKTOP: global switch for the "Hide clean versions when an explicit one exists" setting. The
 * existing filters read this flag instead of taking a new parameter, so the ~30 screens and view
 * models do not have to change.
 */
object CleanDuplicates {
    @Volatile
    var enabled: Boolean = false
}

// DESKTOP: only clean/explicit markers are edition suffixes we ignore. "(Deluxe)" and similar tags
// describe a genuinely different release, so they stay part of the key.
private val VERSION_TAG_REGEX = Regex(
    """[\(\[](?:[^)\]]*?[-\s])?(?:clean|explicit|edited|radio edit)(?:\s+(?:album\s+)?version|\s+edit)?[\)\]]""",
    RegexOption.IGNORE_CASE,
)

// DESKTOP: clean cuts often drop the "(feat. ...)" suffix from the title while the explicit cut
// keeps it, so featured guests must not affect the key.
private val FEAT_TAG_REGEX = Regex(
    """\s*[\(\[](?:feat\.?|ft\.?|featuring)\s+[^)\]]*[\)\]]""",
    RegexOption.IGNORE_CASE,
)

private val WHITESPACE_REGEX = Regex("""\s+""")

/**
 * Builds the comparison key used to spot the same release in its explicit and clean variants.
 * Only the first artist is used: clean and explicit cuts frequently list different featured
 * guests, and albums from artist pages carry no artist list at all.
 */
// examples:
//   normalizeTitleKey("Song (Clean)", listOf("Artist")) == "song|artist"
//   normalizeTitleKey("Song [Explicit]", listOf("B", "A")) == "song|b"
//   normalizeTitleKey("  Song   (Radio Edit) ", listOf("Artist")) == "song|artist"
//   normalizeTitleKey("Song (feat. Guest)", listOf("Artist", "Guest")) == "song|artist"
fun normalizeTitleKey(title: String, artists: List<String>): String {
    val normalizedTitle = title
        .lowercase()
        .replace(VERSION_TAG_REGEX, " ")
        .replace(FEAT_TAG_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    val primaryArtist = artists
        .asSequence()
        .map { it.lowercase().replace(WHITESPACE_REGEX, " ").trim() }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

    return "$normalizedTitle|$primaryArtist"
}

/** DESKTOP: comparison key for the duplicate filter, or null for item types we do not deduplicate. */
fun YTItem.cleanDuplicateKey(): String? = when (this) {
    is SongItem -> normalizeTitleKey(title, artists.map { it.name })
    is AlbumItem -> normalizeTitleKey(title, artists.orEmpty().map { it.name })
    else -> null
}

/**
 * DESKTOP: removes a clean item when the same list also contains an explicit item with the same
 * non-null [key]. Items without a key and explicit items are always kept, and the original order
 * is preserved. Does nothing while [CleanDuplicates.enabled] is false.
 */
fun <T> List<T>.dropCleanDuplicates(
    isExplicit: (T) -> Boolean,
    key: (T) -> String?,
): List<T> {
    if (!CleanDuplicates.enabled) return this

    val explicitKeys = mutableSetOf<String>()
    for (item in this) {
        if (isExplicit(item)) {
            key(item)?.let(explicitKeys::add)
        }
    }
    if (explicitKeys.isEmpty()) return this

    val result = filter { item ->
        if (isExplicit(item)) {
            true
        } else {
            // Keep clean items that cannot be matched to an explicit twin.
            key(item)?.let { it !in explicitKeys } ?: true
        }
    }
    if (result.size != size) {
        Timber.d("clean duplicates: removed %d of %d items", size - result.size, size)
    }
    return result
}
