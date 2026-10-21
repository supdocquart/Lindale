package com.metrolist.music.utils
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import com.metrolist.music.PlatformContext
fun getArtistSeparator(context: PlatformContext): String = " & "
fun <T> List<T>.joinToArtistString(conjunction: String, transform: (T) -> String): String = joinToString(conjunction) { transform(it) }
fun reportException(throwable: Throwable) { throwable.printStackTrace() }
fun setAppLocale(context: PlatformContext, locale: Any) {}
