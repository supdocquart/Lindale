package com.metrolist.music.extensions

import android.content.ContentResolver
import com.metrolist.music.platform.Uri

fun ContentResolver.fileNameAndSize(uri: Uri): Pair<String, Long> {
    val file = com.metrolist.music.platform.DesktopFiles.toFile(uri)
    if (file != null && file.exists()) return file.name to file.length()
    val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast('\\') ?: "unknown"
    return name to 0L
}
