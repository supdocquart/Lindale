package com.metrolist.music.ui.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Album-art colour extraction modelled on AndroidX Palette: pixels are quantised into
 * 5-bit-per-channel buckets and the target swatches are picked by saturation, lightness
 * and population. Platform code installs [pixelReader] to turn an image into ARGB pixels.
 */
class Palette private constructor(val swatches: List<Swatch>) {
    class Swatch(val rgb: Int, val population: Int) {
        val hsl: FloatArray = rgbToHsl(rgb)
    }

    private val targets = HashMap<Target, Swatch?>()

    private enum class Target(
        val minL: Float, val targetL: Float, val maxL: Float,
        val minS: Float, val targetS: Float, val maxS: Float,
    ) {
        Vibrant(0.3f, 0.5f, 0.7f, 0.35f, 1f, 1f),
        LightVibrant(0.55f, 0.74f, 1f, 0.35f, 1f, 1f),
        DarkVibrant(0f, 0.26f, 0.45f, 0.35f, 1f, 1f),
        Muted(0.3f, 0.5f, 0.7f, 0f, 0.3f, 0.4f),
        LightMuted(0.55f, 0.74f, 1f, 0f, 0.3f, 0.4f),
        DarkMuted(0f, 0.26f, 0.45f, 0f, 0.3f, 0.4f),
    }

    init {
        val used = HashSet<Swatch>()
        val maxPopulation = swatches.maxOfOrNull { it.population }?.coerceAtLeast(1) ?: 1
        for (target in Target.entries) {
            val best = swatches
                .filter { it !in used }
                .filter { s -> s.hsl[1] in target.minS..target.maxS && s.hsl[2] in target.minL..target.maxL }
                .maxByOrNull { s ->
                    0.24f * (1f - abs(s.hsl[1] - target.targetS)) +
                        0.52f * (1f - abs(s.hsl[2] - target.targetL)) +
                        0.24f * (s.population.toFloat() / maxPopulation)
                }
            targets[target] = best
            if (best != null) used += best
        }
    }

    val dominantSwatch: Swatch? get() = swatches.maxByOrNull { it.population }
    val vibrantSwatch: Swatch? get() = targets[Target.Vibrant]
    val mutedSwatch: Swatch? get() = targets[Target.Muted]
    val darkVibrantSwatch: Swatch? get() = targets[Target.DarkVibrant]
    val lightVibrantSwatch: Swatch? get() = targets[Target.LightVibrant]
    val darkMutedSwatch: Swatch? get() = targets[Target.DarkMuted]
    val lightMutedSwatch: Swatch? get() = targets[Target.LightMuted]

    fun getVibrantColor(defaultColor: Int): Int = vibrantSwatch?.rgb ?: defaultColor
    fun getMutedColor(defaultColor: Int): Int = mutedSwatch?.rgb ?: defaultColor
    fun getDarkVibrantColor(defaultColor: Int): Int = darkVibrantSwatch?.rgb ?: defaultColor
    fun getLightVibrantColor(defaultColor: Int): Int = lightVibrantSwatch?.rgb ?: defaultColor
    fun getDarkMutedColor(defaultColor: Int): Int = darkMutedSwatch?.rgb ?: defaultColor
    fun getLightMutedColor(defaultColor: Int): Int = lightMutedSwatch?.rgb ?: defaultColor
    fun getDominantColor(defaultColor: Int): Int = dominantSwatch?.rgb ?: defaultColor

    class Builder(private val image: Any?) {
        private var maxColors = 16
        private var resizeArea = 112 * 112

        fun maximumColorCount(count: Int): Builder = apply { maxColors = count.coerceAtLeast(1) }
        fun resizeBitmapArea(area: Int): Builder = apply { resizeArea = area.coerceAtLeast(1) }

        fun generate(): Palette {
            val pixels = image?.let { img ->
                runCatching { pixelReader?.invoke(img, resizeArea) }
                    .onFailure { timber.log.Timber.w(it, "Palette: could not read pixels") }
                    .getOrNull()
            } ?: return Palette(emptyList())
            return Palette(quantize(pixels, maxColors))
        }
    }

    companion object {
        /** Returns ARGB pixels for an image, downsampled to roughly the given area. Installed by platform code. */
        @Volatile
        var pixelReader: ((image: Any, maxArea: Int) -> IntArray?)? = null

        fun from(bitmap: Any?): Builder = Builder(bitmap)

        private fun quantize(pixels: IntArray, maxColors: Int): List<Swatch> {
            val counts = HashMap<Int, IntArray>()
            for (argb in pixels) {
                if ((argb ushr 24) < 128) continue
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                val acc = counts.getOrPut(key) { IntArray(4) }
                acc[0] += r; acc[1] += g; acc[2] += b; acc[3]++
            }
            val buckets = counts.values.map { a ->
                val n = a[3]
                Swatch(0xFF000000.toInt() or ((a[0] / n) shl 16) or ((a[1] / n) shl 8) or (a[2] / n), n)
            }.filter { !isNearBlackOrWhite(it.hsl) }

            // Greedily merge perceptually close buckets so the result has at most maxColors * 2 swatches.
            val sorted = buckets.sortedByDescending { it.population }.toMutableList()
            val merged = ArrayList<Swatch>()
            for (s in sorted) {
                val idx = merged.indexOfFirst { colorDistance(it.rgb, s.rgb) < 24 }
                if (idx >= 0) {
                    val m = merged[idx]
                    merged[idx] = Swatch(m.rgb, m.population + s.population)
                } else if (merged.size < maxColors * 2) {
                    merged += s
                }
            }
            return merged
        }

        private fun isNearBlackOrWhite(hsl: FloatArray) = hsl[2] <= 0.05f || hsl[2] >= 0.95f

        private fun colorDistance(a: Int, b: Int): Int {
            val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
            val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
            val db = (a and 0xFF) - (b and 0xFF)
            return max(abs(dr), max(abs(dg), abs(db)))
        }

        internal fun rgbToHsl(rgb: Int): FloatArray {
            val r = ((rgb shr 16) and 0xFF) / 255f
            val g = ((rgb shr 8) and 0xFF) / 255f
            val b = (rgb and 0xFF) / 255f
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val l = (maxC + minC) / 2f
            if (maxC == minC) return floatArrayOf(0f, 0f, l)
            val d = maxC - minC
            val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
            val h = when (maxC) {
                r -> ((g - b) / d + (if (g < b) 6f else 0f))
                g -> ((b - r) / d + 2f)
                else -> ((r - g) / d + 4f)
            } * 60f
            return floatArrayOf(h, s, l)
        }
    }
}
