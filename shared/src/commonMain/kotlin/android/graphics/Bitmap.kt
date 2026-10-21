package android.graphics

import java.awt.image.BufferedImage
import java.io.OutputStream
import javax.imageio.ImageIO

/** Desktop bitmap backed by a [BufferedImage]. */
open class Bitmap(val image: BufferedImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)) {
    enum class CompressFormat {
        JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS
    }

    fun compress(format: CompressFormat, quality: Int, stream: OutputStream): Boolean {
        val type = if (format == CompressFormat.JPEG) "jpg" else "png"
        val source = if (type == "jpg" && image.type != BufferedImage.TYPE_INT_RGB) {
            BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also { rgb ->
                rgb.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
            }
        } else image
        return ImageIO.write(source, type, stream)
    }

    val width: Int get() = image.width
    val height: Int get() = image.height

    companion object {
        fun createBitmap(width: Int, height: Int, config: Any? = null): Bitmap =
            Bitmap(BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB))
    }
}
