package com.metrolist.music.platform
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

interface UrlOpener {
    fun open(url: String)
}
