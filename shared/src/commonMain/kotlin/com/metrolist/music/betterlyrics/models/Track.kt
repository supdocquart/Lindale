package com.metrolist.music.betterlyrics.models
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import kotlinx.serialization.Serializable

@Serializable
data class TTMLResponse(
    val ttml: String? = null,
)

@Serializable
data class SearchResponse(
    val results: List<Track>
)

@Serializable
data class Track(
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Double,
    val lyrics: Lyrics? = null
)

@Serializable
data class Lyrics(
    val lines: List<Line>
)

@Serializable
data class Line(
    val text: String,
    val startTime: Double,
    val words: List<Word>? = null
)

@Serializable
data class Word(
    val text: String,
    val startTime: Double,
    val endTime: Double
)
