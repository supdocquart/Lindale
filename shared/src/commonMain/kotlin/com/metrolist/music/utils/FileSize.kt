package com.metrolist.music.utils

import kotlin.math.log10
import kotlin.math.pow

private val sizeUnits = arrayOf("B", "KB", "MB", "GB", "TB")

fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val group = (log10(sizeBytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, sizeUnits.size - 1)
    return "%.1f %s".format(sizeBytes / 1024.0.pow(group), sizeUnits[group])
}
