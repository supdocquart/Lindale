package com.metrolist.music.platform
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

interface AppInfo {
    val versionName: String
    val architecture: String
    val isUpdaterAvailable: Boolean
    val isCastAvailable: Boolean
}
