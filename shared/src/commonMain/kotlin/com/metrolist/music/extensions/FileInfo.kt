package com.metrolist.music.extensions

import com.metrolist.music.platform.DesktopFiles
import com.metrolist.music.platform.Uri

fun fileNameAndSize(uri: Uri): Pair<String, Long> {
    val file = DesktopFiles.toFile(uri)
    if (file != null && file.exists()) return file.name to file.length()
    val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast('\\') ?: "unknown"
    return name to 0L
}
