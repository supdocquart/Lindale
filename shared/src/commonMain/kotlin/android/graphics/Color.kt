package android.graphics

object Color {
    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    fun colorToHSV(color: Int, hsv: FloatArray) {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        val max = maxOf(r, maxOf(g, b))
        val min = minOf(r, minOf(g, b))
        val delta = max - min

        var h = 0f
        if (delta != 0f) {
            when (max) {
                r -> h = (g - b) / delta % 6f
                g -> h = (b - r) / delta + 2f
                b -> h = (r - g) / delta + 4f
            }
            h *= 60f
            if (h < 0f) h += 360f
        }

        val s = if (max == 0f) 0f else delta / max
        val v = max

        if (hsv.isNotEmpty()) hsv[0] = h
        if (hsv.size > 1) hsv[1] = s
        if (hsv.size > 2) hsv[2] = v
    }
}
