package com.metrolist.music.extensions

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun nowLocalDateTime(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

val LocalDateTime.Companion.now: LocalDateTime
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun LocalDateTime.Companion.now(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun java.time.LocalDateTime.toKotlinx(): LocalDateTime =
    kotlinx.datetime.Instant.fromEpochMilliseconds(this.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        .toLocalDateTime(TimeZone.currentSystemDefault())

fun kotlinx.datetime.LocalDateTime.toJava(): java.time.LocalDateTime =
    java.time.LocalDateTime.of(year, monthNumber, dayOfMonth, hour, minute, second, nanosecond)

fun kotlinx.datetime.LocalDateTime.format(formatter: java.time.format.DateTimeFormatter): String =
    toJava().format(formatter)

