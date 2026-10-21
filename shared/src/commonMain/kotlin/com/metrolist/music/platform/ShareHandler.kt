package com.metrolist.music.platform
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

interface ShareHandler {
    fun share(text: String)
}
