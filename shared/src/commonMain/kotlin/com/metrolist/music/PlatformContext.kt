package com.metrolist.music

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream

typealias PlatformContext = Context

val Context.cacheDir: File get() = (this as Context).cacheDir
val Context.filesDir: File get() = (this as Context).filesDir
fun Context.getDatabasePath(name: String): File = (this as Context).getDatabasePath(name)
fun Context.openInputStream(uri: Any): InputStream? = (this as Context).openInputStream(uri)
fun Context.openOutputStream(uri: Any): OutputStream? = (this as Context).openOutputStream(uri)
fun <T> Context.getSystemService(serviceClass: Class<T>): T? = (this as Context).getSystemService(serviceClass)
fun Context.getSystemService(name: String): Any? = (this as Context).getSystemService(name)
