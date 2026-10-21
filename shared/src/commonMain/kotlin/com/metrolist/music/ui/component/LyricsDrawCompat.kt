package com.metrolist.music.ui.component

import androidx.compose.ui.graphics.Canvas

data class GlowTextStyle(
    val color: Int,
    val textSize: Float,
    val blurRadius: Float,
    val bold: Boolean = true,
)

expect fun Canvas.drawGlowText(text: String, x: Float, y: Float, style: GlowTextStyle)
