package android.net

import java.io.File

class Uri(val raw: String = "") {
    val scheme: String? get() = runCatching { java.net.URI(raw).scheme }.getOrNull()
    val host: String? get() = runCatching { java.net.URI(raw).host }.getOrNull()
    val path: String? get() = runCatching { java.net.URI(raw).path }.getOrNull()
    val lastPathSegment: String? get() = path?.trimEnd('/')?.substringAfterLast('/')

    override fun toString(): String = raw

    companion object {
        fun parse(uriString: String): Uri = Uri(uriString)
        fun fromFile(file: File): Uri = Uri(file.toURI().toString())
        fun fromParts(scheme: String, ssp: String, fragment: String?): Uri = Uri("$scheme:$ssp")
    }
}
