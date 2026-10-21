package com.metrolist.music.constants
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import com.metrolist.music.ui.screens.OptionStats
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

enum class StatPeriod {
    WEEK_1,
    MONTH_1,
    MONTH_3,
    MONTH_6,
    YEAR_1,
    ALL,
    ;

    fun toLocalDateTime(): LocalDateTime {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        return when (this) {
            WEEK_1 -> now.minus(7, DateTimeUnit.DAY, tz).toLocalDateTime(tz)
            MONTH_1 -> now.minus(1, DateTimeUnit.MONTH, tz).toLocalDateTime(tz)
            MONTH_3 -> now.minus(3, DateTimeUnit.MONTH, tz).toLocalDateTime(tz)
            MONTH_6 -> now.minus(6, DateTimeUnit.MONTH, tz).toLocalDateTime(tz)
            YEAR_1 -> now.minus(12, DateTimeUnit.MONTH, tz).toLocalDateTime(tz)
            ALL -> LocalDateTime(1970, 1, 1, 0, 0)
        }
    }
}

fun statToPeriod(
    selection: OptionStats,
    test: Int,
): LocalDateTime {
    val now = Clock.System.now()
    val tz = TimeZone.currentSystemDefault()
    return when (selection) {
        OptionStats.WEEKS -> now.minus(test * 7 + 1, DateTimeUnit.DAY, tz).toLocalDateTime(tz)
        OptionStats.MONTHS -> now.minus(test, DateTimeUnit.MONTH, tz).toLocalDateTime(tz)
        OptionStats.YEARS -> now.minus(test * 12, DateTimeUnit.MONTH, tz).toLocalDateTime(tz)
        OptionStats.CONTINUOUS -> {
            val index = if (test >= StatPeriod.entries.size) 0 else test
            StatPeriod.entries[index].toLocalDateTime()
        }
    }
}
