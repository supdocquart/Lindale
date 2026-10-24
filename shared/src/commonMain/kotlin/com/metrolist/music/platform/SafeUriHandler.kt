package com.metrolist.music.platform

// DESKTOP: replaces Compose's default UriHandler so every uriHandler.openUri(...) goes through DesktopShare.openUrl.
object SafeUriHandler : androidx.compose.ui.platform.UriHandler {
    override fun openUri(uri: String) = DesktopShare.openUrl(uri)
}
