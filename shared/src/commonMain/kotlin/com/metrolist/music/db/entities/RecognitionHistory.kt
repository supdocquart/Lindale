/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db.entities
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

@Entity(
    tableName = "recognition_history",
    indices = [
        Index(
            value = ["trackId"],
            unique = false,
        ),
    ],
)
data class RecognitionHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverArtUrl: String? = null,
    val coverArtHqUrl: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val label: String? = null,
    val shazamUrl: String? = null,
    val appleMusicUrl: String? = null,
    val spotifyUrl: String? = null,
    val isrc: String? = null,
    val youtubeVideoId: String? = null,
    val recognizedAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    val liked: Boolean = false
)


