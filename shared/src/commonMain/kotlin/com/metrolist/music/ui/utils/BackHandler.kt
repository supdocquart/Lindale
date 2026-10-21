package com.metrolist.music.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Routes the desktop "back" action (Esc, mouse back button) to the innermost
 * enabled [BackHandler]; falls back to [fallback] (navigation pop) otherwise.
 */
object BackDispatcher {
    class Entry(var enabled: Boolean, val onBack: () -> Unit)

    private val entries = ArrayList<Entry>()

    @Volatile
    var fallback: (() -> Boolean)? = null

    @Synchronized
    internal fun register(entry: Entry) {
        entries += entry
    }

    @Synchronized
    internal fun unregister(entry: Entry) {
        entries -= entry
    }

    /** Returns true when something handled the back action. */
    fun dispatch(): Boolean {
        val target = synchronized(this) { entries.lastOrNull { it.enabled } }
        if (target != null) {
            target.onBack()
            return true
        }
        return fallback?.invoke() ?: false
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val entry = remember { BackDispatcher.Entry(enabled) { currentOnBack() } }
    SideEffect { entry.enabled = enabled }
    DisposableEffect(entry) {
        BackDispatcher.register(entry)
        onDispose { BackDispatcher.unregister(entry) }
    }
}
