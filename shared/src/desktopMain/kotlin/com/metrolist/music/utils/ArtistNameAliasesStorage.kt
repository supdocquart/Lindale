package com.metrolist.music.utils

actual object ArtistNameAliasesStorage {
    actual fun load(): Map<String, String> = emptyMap()
    actual fun save(aliases: Map<String, String>) {}
    actual fun clear() {}
}
