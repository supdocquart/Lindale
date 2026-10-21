package com.metrolist.music.eq

import com.metrolist.music.eq.data.EqStore
import com.metrolist.music.eq.data.FilterType
import com.metrolist.music.eq.data.ParametricEQBand
import com.metrolist.music.eq.data.SavedEQProfile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Audio engine hook: a fixed-band graphic equaliser (VLC on desktop). */
interface EqualizerBackend {
    val bandFrequencies: List<Float>
    val maxGainDb: Float
    fun apply(preampDb: Float, gainsDb: List<Float>)
    fun disable()
}

class EqualizerService {
    fun applyProfile(profile: SavedEQProfile): Result<Unit> = runCatching {
        val engine = backend ?: error("Equalizer is not available (audio engine not ready)")
        applyTo(engine, profile)
    }

    fun disable() {
        backend?.disable()
    }

    fun addAudioProcessor(processor: Any?) {}
    fun removeAudioProcessor(processor: Any?) {}

    companion object {
        @Volatile
        private var backend: EqualizerBackend? = null

        /** Called by the audio engine once it exists; re-applies the persisted active profile. */
        fun attach(engine: EqualizerBackend) {
            backend = engine
            EqStore.activeProfile.value?.let { profile ->
                runCatching { applyTo(engine, profile) }
                    .onFailure { timber.log.Timber.e(it, "EQ: restoring active profile failed") }
            }
        }

        private fun applyTo(engine: EqualizerBackend, profile: SavedEQProfile) {
            val limit = engine.maxGainDb
            val gains = engine.bandFrequencies.map { f ->
                responseDb(profile.bands, f.toDouble()).toFloat().coerceIn(-limit, limit)
            }
            // AutoEq preamps are negative to avoid clipping; keep them, but never boost overall level.
            val preamp = profile.preamp.toFloat().coerceIn(-limit, 0f)
            timber.log.Timber.i("EQ: applying %s preamp=%.1f gains=%s", profile.name, preamp, gains.joinToString { "%.1f".format(it) })
            engine.apply(preamp, gains)
        }

        private const val SAMPLE_RATE = 48_000.0

        /** Combined magnitude response in dB of the parametric filters at [freq]. */
        fun responseDb(bands: List<ParametricEQBand>, freq: Double): Double =
            bands.filter { it.enabled }.sumOf { biquadDb(it, freq) }

        private fun biquadDb(band: ParametricEQBand, freq: Double): Double {
            val q = band.q.takeIf { it > 0 } ?: 0.707
            val a = 10.0.pow(band.gain / 40.0)
            val w0 = 2 * PI * band.frequency / SAMPLE_RATE
            val alpha = sin(w0) / (2 * q)
            val cw = cos(w0)
            val (b, aCoef) = when (band.filterType) {
                FilterType.PK -> doubleArrayOf(1 + alpha * a, -2 * cw, 1 - alpha * a) to
                    doubleArrayOf(1 + alpha / a, -2 * cw, 1 - alpha / a)
                FilterType.LSC -> {
                    val s = 2 * sqrt(a) * alpha
                    doubleArrayOf(a * ((a + 1) - (a - 1) * cw + s), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - s)) to
                        doubleArrayOf((a + 1) + (a - 1) * cw + s, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - s)
                }
                FilterType.HSC -> {
                    val s = 2 * sqrt(a) * alpha
                    doubleArrayOf(a * ((a + 1) + (a - 1) * cw + s), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - s)) to
                        doubleArrayOf((a + 1) - (a - 1) * cw + s, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - s)
                }
                FilterType.LPQ -> doubleArrayOf((1 - cw) / 2, 1 - cw, (1 - cw) / 2) to doubleArrayOf(1 + alpha, -2 * cw, 1 - alpha)
                FilterType.HPQ -> doubleArrayOf((1 + cw) / 2, -(1 + cw), (1 + cw) / 2) to doubleArrayOf(1 + alpha, -2 * cw, 1 - alpha)
            }
            val w = 2 * PI * freq / SAMPLE_RATE
            val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
            val numRe = b[0] + b[1] * c1 + b[2] * c2
            val numIm = -(b[1] * s1 + b[2] * s2)
            val denRe = aCoef[0] + aCoef[1] * c1 + aCoef[2] * c2
            val denIm = -(aCoef[1] * s1 + aCoef[2] * s2)
            val mag2 = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm)
            return 10 * log10(mag2.coerceAtLeast(1e-12))
        }
    }
}
