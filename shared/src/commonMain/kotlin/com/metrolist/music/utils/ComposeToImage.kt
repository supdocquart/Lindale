package com.metrolist.music.utils

import androidx.compose.ui.text.style.TextAlign
import com.metrolist.music.platform.DesktopFiles
import com.metrolist.music.platform.DesktopShare
import com.metrolist.music.ui.component.LyricsBackgroundStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.File
import javax.imageio.ImageIO

/** Renders the shareable lyrics card with Java2D on desktop. */
object ComposeToImage {
    suspend fun createLyricsImage(
        context: Any?,
        coverArtUrl: String? = null,
        songTitle: String = "",
        artistName: String = "",
        lyrics: String = "",
        width: Int = 1080,
        height: Int = 1920,
        backgroundColor: Int = 0,
        backgroundStyle: Any? = null,
        textColor: Int = 0,
        secondaryTextColor: Int = 0,
        lyricsAlignment: Any? = null,
        vararg args: Any?,
    ): BufferedImage = withContext(Dispatchers.IO) {
        val w = width.takeIf { it in 200..4000 } ?: 1080
        val h = height.takeIf { it in 200..4000 } ?: 1920
        val cover = coverArtUrl?.let { url -> runCatching { ImageIO.read(java.net.URI(url).toURL()) }.getOrNull() }
        val bg = Color(backgroundColor or (0xFF shl 24), true)
        val fg = Color(textColor.takeIf { it != 0 } ?: 0xFFFFFFFF.toInt(), true)
        val fg2 = Color(secondaryTextColor.takeIf { it != 0 } ?: 0xB3FFFFFF.toInt(), true)

        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

        when (backgroundStyle) {
            LyricsBackgroundStyle.GRADIENT -> {
                g.paint = GradientPaint(0f, 0f, bg, w.toFloat(), h.toFloat(), bg.darker().darker())
                g.fillRect(0, 0, w, h)
            }
            LyricsBackgroundStyle.BLUR -> {
                g.color = bg
                g.fillRect(0, 0, w, h)
                if (cover != null) {
                    g.drawImage(blur(cover), 0, 0, w, h, null)
                    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f)
                    g.color = Color.BLACK
                    g.fillRect(0, 0, w, h)
                    g.composite = AlphaComposite.SrcOver
                }
            }
            else -> {
                g.color = bg
                g.fillRect(0, 0, w, h)
            }
        }

        val pad = (w * 0.08).toInt()
        val coverSize = (w * 0.16).toInt()
        var y = pad
        if (cover != null) {
            val clip = RoundRectangle2D.Float(pad.toFloat(), y.toFloat(), coverSize.toFloat(), coverSize.toFloat(), coverSize * 0.2f, coverSize * 0.2f)
            val oldClip = g.clip
            g.clip(clip)
            g.drawImage(cover, pad, y, coverSize, coverSize, null)
            g.clip = oldClip
        }
        val textX = if (cover != null) pad + coverSize + pad / 2 else pad
        val titleFont = Font(Font.SANS_SERIF, Font.BOLD, (w * 0.045).toInt())
        val artistFont = Font(Font.SANS_SERIF, Font.PLAIN, (w * 0.035).toInt())
        g.font = titleFont
        g.color = fg
        g.drawString(ellipsize(g, songTitle, w - textX - pad), textX, y + coverSize / 2 - 4)
        g.font = artistFont
        g.color = fg2
        g.drawString(ellipsize(g, artistName, w - textX - pad), textX, y + coverSize / 2 + g.fontMetrics.height)
        y += coverSize + pad

        val lyricsFont = Font(Font.SANS_SERIF, Font.BOLD, (w * 0.06).toInt())
        g.font = lyricsFont
        g.color = fg
        val maxWidth = w - pad * 2
        val lines = lyrics.lines().flatMap { wrap(g, it, maxWidth) }
        val lineHeight = (g.fontMetrics.height * 1.15).toInt()
        val maxLines = ((h - y - pad * 2) / lineHeight).coerceAtLeast(1)
        for (line in lines.take(maxLines)) {
            val lw = g.fontMetrics.stringWidth(line)
            val x = when (lyricsAlignment) {
                TextAlign.Center -> (w - lw) / 2
                TextAlign.End, TextAlign.Right -> w - pad - lw
                else -> pad
            }
            y += lineHeight
            g.drawString(line, x, y)
        }

        g.font = Font(Font.SANS_SERIF, Font.BOLD, (w * 0.03).toInt())
        g.color = fg2
        g.drawString("Metrolist", pad, h - pad)
        g.dispose()
        img
    }

    private fun ellipsize(g: java.awt.Graphics2D, text: String, max: Int): String {
        if (g.fontMetrics.stringWidth(text) <= max) return text
        var t = text
        while (t.isNotEmpty() && g.fontMetrics.stringWidth("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    private fun wrap(g: java.awt.Graphics2D, text: String, max: Int): List<String> {
        if (text.isBlank()) return listOf("")
        val out = ArrayList<String>()
        var current = StringBuilder()
        for (word in text.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (g.fontMetrics.stringWidth(candidate) <= max || current.isEmpty()) {
                current = StringBuilder(candidate)
            } else {
                out += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private fun blur(src: BufferedImage): BufferedImage {
        val small = BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB)
        small.createGraphics().apply { drawImage(src, 0, 0, 48, 48, null); dispose() }
        val k = FloatArray(25) { 1f / 25f }
        return ConvolveOp(Kernel(5, 5, k), ConvolveOp.EDGE_NO_OP, null).filter(small, null)
    }

    private val shareDir: File
        get() = File(DesktopFiles.cacheDir, "share").apply { mkdirs() }

    fun saveBitmapAsFile(context: Any?, bitmap: Any?, fileName: String): File? {
        val image = bitmap as? BufferedImage ?: return null
        val file = File(shareDir, if (fileName.endsWith(".png")) fileName else "$fileName.png")
        return if (ImageIO.write(image, "png", file)) file else null
    }

    /** Saves into Pictures\Metrolist and reports the path. */
    suspend fun saveBitmap(context: Any?, bitmap: Any?, fileName: String): File? = withContext(Dispatchers.IO) {
        val file = saveBitmapAsFile(context, bitmap, fileName) ?: return@withContext null
        val home = File(System.getProperty("user.home"))
        val pictures = File(home, "Pictures").takeIf { it.isDirectory } ?: home
        val target = File(File(pictures, "Metrolist").apply { mkdirs() }, file.name)
        file.copyTo(target, overwrite = true)
        ToastHost.post(target.absolutePath, Toast.LENGTH_LONG)
        target
    }

    fun shareBitmap(context: Any?, bitmap: Any?, fileName: String) {
        DesktopShare.shareFile(saveBitmapAsFile(context, bitmap, fileName))
    }
}
