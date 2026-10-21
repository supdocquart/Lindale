package com.metrolist.music.discord
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import timber.log.Timber

object DiscordTemplateRenderer {
    private const val TAG = "DiscordSvc"

    fun render(
        template: String,
        title: String,
        artist: String,
        album: String?,
        songId: String = "",
    ): String {
        var result = template
            .replace("{song.name}", title)
            .replace("{song.id}", songId)
            .replace("{artist.name}", artist)
            .replace("{album.name}", album ?: DiscordDefaults.UNKNOWN_ALBUM)
        Timber.tag(TAG).v("render: template=%s -> result=%s", template, result)
        return result
    }

    val PLACEHOLDERS = listOf(
        "{song.name}",
        "{artist.name}",
        "{album.name}",
        "{song.id}",
    )
}
