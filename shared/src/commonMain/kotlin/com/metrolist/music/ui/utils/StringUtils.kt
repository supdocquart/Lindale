/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource


import java.text.DecimalFormat

fun numberFormatter(n: Int) =
    DecimalFormat("#,###")
        .format(n)
        .replace(",", ".")

@androidx.compose.runtime.Composable
fun getLoudnessLevelLabel(level: com.metrolist.music.constants.LoudnessLevel): String =
    when (level) {
        com.metrolist.music.constants.LoudnessLevel.AGGRESSIVE -> stringResource(Res.string.loudness_level_aggressive)
        com.metrolist.music.constants.LoudnessLevel.LOUD -> stringResource(Res.string.loudness_level_loud)
        com.metrolist.music.constants.LoudnessLevel.BALANCED -> stringResource(Res.string.loudness_level_balanced)
        com.metrolist.music.constants.LoudnessLevel.QUIET -> stringResource(Res.string.loudness_level_quiet)
    }

