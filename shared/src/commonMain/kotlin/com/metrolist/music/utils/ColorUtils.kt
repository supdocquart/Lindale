package com.metrolist.music.utils

/** Fills [hsv] with hue (0..360), saturation and value (0..1) for an ARGB colour. */
fun colorToHSV(argb: Int, hsv: FloatArray) {
    val hsb = java.awt.Color.RGBtoHSB((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, null)
    if (hsv.isNotEmpty()) hsv[0] = hsb[0] * 360f
    if (hsv.size > 1) hsv[1] = hsb[1]
    if (hsv.size > 2) hsv[2] = hsb[2]
}
