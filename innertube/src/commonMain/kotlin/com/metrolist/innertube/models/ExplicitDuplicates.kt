package com.metrolist.innertube.models

/**
 * DESKTOP: global switch for the "Hide explicit duplicates" setting. The existing filters read this
 * flag instead of taking a new parameter, so the ~30 screens and view models do not have to change.
 */
object ExplicitDuplicates {
    @Volatile
    var enabled: Boolean = false
}

private val VERSION_TAG_REGEX = Regex(
    """\((?:clean|explicit|edited|clean version|radio edit)[^)]*\)|\[(?:clean|explicit|edited|clean version|radio edit)[^\]]*\]""",
    RegexOption.IGNORE_CASE,
)

private val WHITESPACE_REGEX = Regex("""\s+""")

/** Builds the comparison key used to spot the same release in its explicit and clean variants. */
// examples:
//   normalizeTitleKey("Song (Clean)", listOf("Artist")) == "song|artist"
//   normalizeTitleKey("Song [Explicit]", listOf("B", "A")) == "song|a, b"
//   normalizeTitleKey("  Song   (Radio Edit) ", listOf("Artist")) == "song|artist"
fun normalizeTitleKey(title: String, artists: List<String>): String {
    val normalizedTitle = title
        .lowercase()
        .replace(VERSION_TAG_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    val normalizedArtists = artists
        .map { it.lowercase().replace(WHITESPACE_REGEX, " ").trim() }
        .filter { it.isNotEmpty() }
        .sorted()
        .joinToString(", ")

    return "$normalizedTitle|$normalizedArtists"
}

/**
 * DESKTOP: removes an explicit item when the same list also contains a non-explicit item with the
 * same non-null [key]. Items without a key and explicit items without a clean twin are kept, and the
 * original order is preserved. Does nothing while [ExplicitDuplicates.enabled] is false.
 */
fun <T> List<T>.dropExplicitDuplicates(
    isExplicit: (T) -> Boolean,
    key: (T) -> String?,
): List<T> {
    if (!ExplicitDuplicates.enabled) return this

    val cleanKeys = mutableSetOf<String>()
    for (item in this) {
        if (!isExplicit(item)) {
            key(item)?.let(cleanKeys::add)
        }
    }
    if (cleanKeys.isEmpty()) return this

    return filter { item ->
        if (!isExplicit(item)) {
            true
        } else {
            // Keep explicit items that cannot be matched to a clean twin.
            key(item)?.let { it !in cleanKeys } ?: true
        }
    }
}
