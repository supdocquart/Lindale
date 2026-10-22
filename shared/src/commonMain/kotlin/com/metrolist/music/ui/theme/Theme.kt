package com.metrolist.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFFED5564)

// DESKTOP: Windows has no wallpaper-based dynamic colors, so every theme (including the default)
// is generated from a seed color with MaterialKolor, like upstream does for non-default seeds.
@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val baseColorScheme = rememberDynamicColorScheme(
        seedColor = themeColor,
        isDark = darkTheme,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        style = PaletteStyle.TonalSpot,
    )
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) baseColorScheme.pureBlack(true) else baseColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/** Picks the seed color for the app theme from an album cover (any image [Palette] can read). */
fun extractThemeColor(image: Any): Color {
    val palette = Palette.from(image).maximumColorCount(8).generate()
    return Color(palette.rankedColors(1, DefaultThemeColor.toArgb()).first())
}

internal fun Palette.rankedColors(
    desiredColorCount: Int,
    fallbackColor: Int,
): List<Int> = Score.score(
    swatches.associate { it.rgb to it.population },
    desiredColorCount,
    fallbackColor,
    true,
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black,
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
