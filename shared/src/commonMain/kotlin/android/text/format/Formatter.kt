package android.text.format

object Formatter {
    fun formatFileSize(context: Any?, sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val formatted = sizeBytes / Math.pow(1024.0, digitGroups.toDouble())
        return "%.1f %s".format(formatted, units[digitGroups])
    }

    fun formatShortFileSize(context: Any?, sizeBytes: Long): String = formatFileSize(context, sizeBytes)
}
