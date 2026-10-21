package com.metrolist.music.platform

import org.koin.dsl.module
import java.awt.Desktop
import java.net.URI

class DesktopUrlOpener : UrlOpener {
    override fun open(url: String) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}

class DesktopShareHandler : ShareHandler {
    override fun share(text: String) {
        // Simple clipboard fallback for sharing on desktop
        val selection = java.awt.datatransfer.StringSelection(text)
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }
}

class DesktopAppInfo : AppInfo {
    override val versionName: String = "1.0.0-desktop"
    override val architecture: String = System.getProperty("os.arch")
    override val isUpdaterAvailable: Boolean = false // No APK updater on Desktop
    override val isCastAvailable: Boolean = false // Cast not supported on desktop MVP
}

actual val platformModule = module {
    single<UrlOpener> { DesktopUrlOpener() }
    single<ShareHandler> { DesktopShareHandler() }
    single<AppInfo> { DesktopAppInfo() }
}
