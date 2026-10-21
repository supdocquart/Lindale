package com.metrolist.music.ui.theme

import androidx.compose.ui.graphics.Color

object PlayerColorExtractor {
    fun extract(image: Any) {}

    fun extractGradientColors(palette: Palette, fallbackColor: Int): List<Color> {
        val vibrant = palette.getVibrantColor(fallbackColor)
        val darkVibrant = palette.getDarkVibrantColor(fallbackColor)
        return listOf(Color(vibrant), Color(darkVibrant))
    }
}
