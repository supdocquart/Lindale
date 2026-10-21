package com.metrolist.music

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream


actual val PlatformContext.cacheDir: File
    get() = cacheDir

actual val PlatformContext.filesDir: File
    get() = filesDir

actual fun PlatformContext.openInputStream(uri: Any): InputStream? = runCatching {
    if (uri is Uri) contentResolver.openInputStream(uri) else File(uri.toString()).inputStream()
}.getOrNull()

actual fun PlatformContext.openOutputStream(uri: Any): OutputStream? = runCatching {
    if (uri is Uri) contentResolver.openOutputStream(uri) else File(uri.toString()).outputStream()
}.getOrNull()

actual fun PlatformContext.getDatabasePath(name: String): File = (this as Context).getDatabasePath(name)

actual val PlatformContext.packageName: String
    get() = packageName

actual val PlatformContext.packageManager: android.content.pm.PackageManager
    get() = packageManager as Any as android.content.pm.PackageManager

actual fun <T> PlatformContext.getSystemService(serviceClass: Class<T>): T? = getSystemService(serviceClass)

actual fun PlatformContext.getSystemService(name: String): Any? = (this as Context).getSystemService(name)

actual fun PlatformContext.startActivity(intent: Any?) {
    if (intent is android.content.Intent) startActivity(intent)
}
