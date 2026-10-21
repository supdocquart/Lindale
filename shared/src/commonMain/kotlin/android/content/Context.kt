package android.content

import metrolist.shared.generated.resources.Res
import metrolist.shared.generated.resources.copied

import java.io.File
import java.io.InputStream
import java.io.OutputStream

open class Context {
    open val packageName: String get() = "com.metrolist.music"
    open val packageManager: android.content.pm.PackageManager get() = android.content.pm.PackageManager
    open val contentResolver: ContentResolver get() = ContentResolver
    open val applicationContext: Context get() = this
    open val cacheDir: File
        get() = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Metrolist/cache").apply { mkdirs() }
    open val filesDir: File
        get() = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Metrolist").apply { mkdirs() }

    open fun getDatabasePath(name: String): File = File(filesDir, name)
    open fun openInputStream(uri: Any): InputStream? = runCatching {
        val str = uri.toString()
        if (str.startsWith("file:")) java.net.URI(str).toURL().openStream() else File(str).inputStream()
    }.getOrNull()
    open fun openOutputStream(uri: Any): OutputStream? = runCatching {
        val str = uri.toString()
        if (str.startsWith("file:")) File(java.net.URI(str)).outputStream() else File(str).outputStream()
    }.getOrNull()

    open fun <T> getSystemService(serviceClass: Class<T>): T? = null
    open fun getSystemService(name: String): Any? =
        if (name == CLIPBOARD_SERVICE) ClipboardManager() else null

    open fun startActivity(intent: Any?) {
        val realIntent = intent as? Intent ?: return
        runCatching {
            when (realIntent.action) {
                Intent.ACTION_VIEW -> {
                    val url = realIntent.data?.toString()
                    if (!url.isNullOrBlank() && java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                    }
                }
                Intent.ACTION_SEND -> {
                    val stream = com.metrolist.music.platform.DesktopFiles.toFile(realIntent.getParcelableExtra(Intent.EXTRA_STREAM))
                    val text = realIntent.getStringExtra(Intent.EXTRA_TEXT)
                    if (stream != null && stream.isFile) {
                        // Desktop has no share sheet: offer "save as" for shared files.
                        val target = com.metrolist.music.platform.DesktopFiles.saveFile(suggestedName = stream.name)
                        if (target != null) {
                            stream.copyTo(target, overwrite = true)
                            com.metrolist.music.utils.ToastHost.post(target.absolutePath)
                        }
                    } else if (!text.isNullOrBlank()) {
                        val selection = java.awt.datatransfer.StringSelection(text)
                        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                        com.metrolist.music.utils.ToastHost.post(com.metrolist.music.utils.ResourceText.get(Res.string.copied))
                    }
                }
            }
        }
    }
    open fun getString(resId: Any, vararg args: Any?): String = com.metrolist.music.utils.ResourceText.resolve(resId, *args)
    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = FileSharedPreferences.get(filesDir, name)

    companion object {
        const val CLIPBOARD_SERVICE: String = "clipboard"
        const val AUDIO_SERVICE: String = "audio"
        const val NOTIFICATION_SERVICE: String = "notification"
        const val POWER_SERVICE: String = "power"
        const val ALARM_SERVICE: String = "alarm"
        const val MODE_PRIVATE: Int = 0
    }
}
