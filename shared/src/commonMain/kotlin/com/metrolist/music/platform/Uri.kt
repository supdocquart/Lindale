package com.metrolist.music.platform

import java.io.File

/** Minimal URI value used by the UI for local files and links. */
class Uri(val raw: String = "") {
    val scheme: String? get() = runCatching { java.net.URI(raw).scheme }.getOrNull()
    val host: String? get() = runCatching { java.net.URI(raw).host }.getOrNull()
    val path: String? get() = runCatching { java.net.URI(raw).path }.getOrNull()
    val lastPathSegment: String? get() = path?.trimEnd('/')?.substringAfterLast('/')

    override fun toString(): String = raw
    override fun equals(other: Any?): Boolean = other is Uri && other.raw == raw
    override fun hashCode(): Int = raw.hashCode()

    companion object {
        fun parse(uriString: String): Uri = Uri(uriString)
        fun fromFile(file: File): Uri = Uri(file.toURI().toString())
    }
}

fun String.toUri(): Uri = Uri.parse(this)
fun File.toUri(): Uri = Uri.fromFile(this)
