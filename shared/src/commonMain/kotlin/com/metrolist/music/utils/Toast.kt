package com.metrolist.music.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import java.util.Locale

object Toast {
    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1

    fun makeText(context: Any?, message: Any?, duration: Int = LENGTH_SHORT): ToastWrapper =
        ToastWrapper(ResourceText.resolve(message), duration)
}

class ToastWrapper(val message: String, val duration: Int = Toast.LENGTH_SHORT) {
    fun show() = ToastHost.post(message, duration)

    fun cancel() {}
}

data class ToastMessage(val text: String, val durationMs: Long, val id: Long = System.nanoTime())

/** Global queue drained by the in-app toast overlay. */
object ToastHost {
    private val _messages = MutableSharedFlow<ToastMessage>(extraBufferCapacity = 32)
    val messages: SharedFlow<ToastMessage> = _messages.asSharedFlow()

    fun post(text: String, duration: Int = Toast.LENGTH_SHORT) {
        if (text.isBlank()) return
        println("Toast: $text")
        _messages.tryEmit(ToastMessage(text, if (duration == Toast.LENGTH_LONG) 3500L else 2000L))
    }
}

/** Resolves compose string resources outside composition (toasts, view models, share sheets). */
object ResourceText {
    fun resolve(message: Any?, vararg args: Any?): String = when (message) {
        null -> ""
        is StringResource -> get(message, *args)
        is CharSequence -> message.toString()
        else -> message.toString()
    }

    fun get(resource: StringResource, vararg args: Any?): String {
        val template = runCatching { runBlocking { org.jetbrains.compose.resources.getString(resource) } }
            .getOrElse { return "" }
        return format(template, args)
    }

    fun plural(resource: PluralStringResource, quantity: Int, vararg args: Any?): String {
        val template = runCatching { runBlocking { org.jetbrains.compose.resources.getPluralString(resource, quantity) } }
            .getOrElse { return "" }
        return format(template, args)
    }

    private fun format(template: String, args: Array<out Any?>): String =
        if (args.isEmpty()) template
        else runCatching { String.format(Locale.getDefault(), template, *args) }.getOrDefault(template)
}
