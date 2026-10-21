package com.yalantis.ucrop

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.metrolist.music.platform.DesktopFiles
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** Desktop stand-in for uCrop: performs an automatic centred crop to the requested aspect ratio. */
class UCrop private constructor(
    private val source: Uri,
    private val destination: Uri,
) {
    private var aspectX = 0f
    private var aspectY = 0f

    class Options {
        fun setCompressionFormat(format: Bitmap.CompressFormat): Options = this
        fun setCompressionQuality(quality: Int): Options = this
        fun setHideBottomControls(hide: Boolean): Options = this
        fun setToolbarTitle(title: String): Options = this
        fun setStatusBarLight(light: Boolean): Options = this
        fun setToolbarColor(color: Int): Options = this
        fun setToolbarWidgetColor(color: Int): Options = this
        fun setRootViewBackgroundColor(color: Int): Options = this
        fun setLogoColor(color: Int): Options = this
    }

    fun withAspectRatio(x: Float, y: Float): UCrop = apply {
        aspectX = x
        aspectY = y
    }

    fun withOptions(options: Options): UCrop = this

    fun getIntent(context: Context): Intent =
        Intent(ACTION_CROP)
            .putExtra(EXTRA_SOURCE, source)
            .putExtra(EXTRA_DESTINATION, destination)
            .putExtra(EXTRA_ASPECT_X, aspectX)
            .putExtra(EXTRA_ASPECT_Y, aspectY)

    companion object {
        const val ACTION_CROP = "com.yalantis.ucrop.CROP"
        private const val EXTRA_SOURCE = "ucrop.source"
        private const val EXTRA_DESTINATION = "ucrop.destination"
        private const val EXTRA_OUTPUT = "ucrop.output"
        private const val EXTRA_ASPECT_X = "ucrop.aspectX"
        private const val EXTRA_ASPECT_Y = "ucrop.aspectY"

        fun of(source: Uri, destination: Uri): UCrop = UCrop(source, destination)

        fun getOutput(intent: Intent): Uri? = intent.getParcelableExtra(EXTRA_OUTPUT) as? Uri

        /** Crops the source image into the destination file; stores and returns the output URI on success. */
        fun performCrop(intent: Intent): Uri? = runCatching {
            val src = DesktopFiles.toFile(intent.getParcelableExtra(EXTRA_SOURCE)) ?: return null
            val destUri = intent.getParcelableExtra(EXTRA_DESTINATION) as? Uri ?: return null
            val dest = DesktopFiles.toFile(destUri) ?: return null
            val image = ImageIO.read(src) ?: return null

            val ax = (intent.getParcelableExtra(EXTRA_ASPECT_X) as? Float) ?: 0f
            val ay = (intent.getParcelableExtra(EXTRA_ASPECT_Y) as? Float) ?: 0f
            val (w, h) = if (ax > 0f && ay > 0f) {
                val target = ax / ay
                if (image.width.toFloat() / image.height > target) {
                    (image.height * target).toInt() to image.height
                } else {
                    image.width to (image.width / target).toInt()
                }
            } else {
                image.width to image.height
            }
            val x = (image.width - w) / 2
            val y = (image.height - h) / 2

            val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val g = out.createGraphics()
            g.drawImage(image, 0, 0, w, h, x, y, x + w, y + h, null)
            g.dispose()

            dest.parentFile?.mkdirs()
            if (!ImageIO.write(out, "jpg", dest)) return null
            intent.putExtra(EXTRA_OUTPUT, destUri)
            destUri
        }.onFailure { timber.log.Timber.e(it, "UCrop: crop failed") }.getOrNull()
    }
}
