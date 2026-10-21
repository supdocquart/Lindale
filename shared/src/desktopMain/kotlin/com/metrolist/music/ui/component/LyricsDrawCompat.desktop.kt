package com.metrolist.music.ui.component

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint as SkiaPaint

actual fun Canvas.drawGlowText(text: String, x: Float, y: Float, paint: Any?) {
    val androidPaint = paint as? Paint ?: return
    val skiaPaint = SkiaPaint().apply {
        isAntiAlias = androidPaint.isAntiAlias
        color = androidPaint.color
        alpha = androidPaint.alpha.coerceIn(0, 255)
        val blur = androidPaint.maskFilter as? BlurMaskFilter
        if (blur != null && blur.radius > 0f) {
            maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blur.radius / 2f)
        }
    }
    val font = Font(null, androidPaint.textSize.coerceAtLeast(12f))
    nativeCanvas.drawString(text, x, y, font, skiaPaint)
}
