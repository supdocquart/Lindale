package com.metrolist.music.ui.theme

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.awt.image.BufferedImage
import kotlin.math.sqrt

/** Reads ARGB pixels from the image types coil and the UI hand to [Palette] on desktop. */
object DesktopPaletteReader {
    fun install() {
        Palette.pixelReader = ::read
    }

    private fun read(image: Any, maxArea: Int): IntArray? = when (image) {
        is Bitmap -> fromSkia(image, maxArea)
        is BufferedImage -> fromAwt(image, maxArea)
        is ImageBitmap -> fromAwt(image.toAwtImage(), maxArea)
        else -> null
    }

    private fun step(width: Int, height: Int, maxArea: Int): Int {
        val area = width.toLong() * height
        return if (area <= maxArea) 1 else sqrt(area.toDouble() / maxArea).toInt().coerceAtLeast(1)
    }

    private fun fromSkia(bitmap: Bitmap, maxArea: Int): IntArray? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val bytes = bitmap.readPixels(info, w * 4, 0, 0) ?: return null
        val s = step(w, h, maxArea)
        val out = ArrayList<Int>((w / s + 1) * (h / s + 1))
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val i = (y * w + x) * 4
                val b = bytes[i].toInt() and 0xFF
                val g = bytes[i + 1].toInt() and 0xFF
                val r = bytes[i + 2].toInt() and 0xFF
                val a = bytes[i + 3].toInt() and 0xFF
                out += (a shl 24) or (r shl 16) or (g shl 8) or b
                x += s
            }
            y += s
        }
        return out.toIntArray()
    }

    private fun fromAwt(image: BufferedImage, maxArea: Int): IntArray {
        val s = step(image.width, image.height, maxArea)
        val out = ArrayList<Int>()
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                out += image.getRGB(x, y)
                x += s
            }
            y += s
        }
        return out.toIntArray()
    }
}
