package com.metrolist.music.ui.component
import org.jetbrains.skia.Paint as SkiaPaint

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint

private val boldTypeface by lazy { FontMgr.default.matchFamilyStyle(null, FontStyle.BOLD) }

actual fun Canvas.drawGlowText(text: String, x: Float, y: Float, style: GlowTextStyle) {
    val paint = Paint().apply {
        isAntiAlias = true
        color = style.color
        if (style.blurRadius > 0f) {
            maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, style.blurRadius / 2f)
        }
    }
    val font = Font(if (style.bold) boldTypeface else null, style.textSize.coerceAtLeast(12f))
    nativeCanvas.drawString(text, x, y, font, paint)
}
