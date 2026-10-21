package com.metrolist.music.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.koin.dsl.module

class AndroidUrlOpener(private val context: Context) : UrlOpener {
    override fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

class AndroidShareHandler(private val context: Context) : ShareHandler {
    override fun share(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

class AndroidAppInfo : AppInfo {
    override val versionName: String = "1.0.0" // Ideally from BuildConfig
    override val architecture: String = System.getProperty("os.arch") ?: "unknown"
    override val isUpdaterAvailable: Boolean = true
    override val isCastAvailable: Boolean = true
}

actual val platformModule = module {
    single<UrlOpener> { AndroidUrlOpener(get()) }
    single<ShareHandler> { AndroidShareHandler(get()) }
    single<AppInfo> { AndroidAppInfo() }
}
