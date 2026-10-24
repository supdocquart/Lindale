package com.metrolist.music.utils

import com.metrolist.music.constants.LoudnessLevel

/**
 * DESKTOP: shared copy of the upstream gain calculation, which lives inside the Android
 * MusicService and is therefore not reachable from the desktop player connection.
 */
internal fun normalizationGainMb(
    loudnessDb: Double?,
    perceptualLoudnessDb: Double?,
    targetLufs: Float,
): Int? {
    val measuredLufs = perceptualLoudnessDb ?: loudnessDb?.let { it + LoudnessLevel.AGGRESSIVE.targetLufs }
    return measuredLufs
        ?.let { (-(it - targetLufs) * 100.0).toInt() }
        ?.coerceIn(-1500, 300)
}
