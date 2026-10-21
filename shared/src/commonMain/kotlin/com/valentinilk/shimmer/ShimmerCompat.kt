package com.valentinilk.shimmer

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

data class ShimmerThemeData(
    val animationSpec: AnimationSpec<Float>? = null,
    val shaderColors: List<Color> = emptyList(),
)

val defaultShimmerTheme = ShimmerThemeData()

fun Modifier.shimmer(): Modifier = this
