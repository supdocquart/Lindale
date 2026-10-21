package com.metrolist.music.ui.component

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas

actual fun Canvas.drawGlowText(text: String, x: Float, y: Float, paint: Any?) {
    nativeCanvas.drawText(text, x, y, paint as android.graphics.Paint)
}
