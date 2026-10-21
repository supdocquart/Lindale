package com.metrolist.music.db
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDateTime? =
        if (value != null) {
            Instant.fromEpochMilliseconds(value).toLocalDateTime(TimeZone.UTC)
        } else {
            null
        }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): Long? =
        date?.toInstant(TimeZone.UTC)?.toEpochMilliseconds()
}
