package com.metrolist.music.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * DESKTOP: JBR treats the whole custom title bar as a native caption, which is what makes
 * dragging, double-click maximize and Aero Snap work. Buttons and the search field must opt out
 * of that so they receive normal Compose input. Interactive elements register their bounds here
 * and the title row's pointer handler toggles JBR's hit test while the pointer is over them.
 */
class TitleBarInteraction {
    private data class Area(val owner: Any, val rect: Rect)

    private val areas = mutableListOf<Area>()
    private val lock = Any()
    private var interactive = false
    private var published = false

    fun register(owner: Any, rect: Rect) {
        synchronized(lock) {
            areas.removeAll { it.owner === owner }
            areas.add(Area(owner, rect))
        }
    }

    fun unregister(owner: Any) {
        synchronized(lock) {
            areas.removeAll { it.owner === owner }
        }
    }

    private fun contains(position: Offset): Boolean = synchronized(lock) {
        areas.any { it.rect.contains(position) }
    }

    fun onPointer(type: PointerEventType, position: Offset, pressed: Boolean) {
        val inside = contains(position)
        val next = when (type) {
            PointerEventType.Exit -> false
            PointerEventType.Press -> inside
            else -> if (pressed) interactive || inside else inside
        }
        interactive = next
        // DESKTOP: JBR's forceHitTest applies per mouse event, so publish on every event.
        published = next
        WindowChrome.setTitleBarInteractive(next)
    }

    fun reset() {
        interactive = false
        if (published) {
            published = false
            WindowChrome.setTitleBarInteractive(false)
        }
    }
}

@Composable
fun Modifier.titleBarInteractive(interaction: TitleBarInteraction): Modifier {
    val owner = remember { Any() }
    DisposableEffect(interaction, owner) {
        onDispose { interaction.unregister(owner) }
    }
    return onGloballyPositioned { coordinates ->
        val origin = coordinates.positionInRoot()
        val size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
        interaction.register(owner, Rect(origin, size))
    }
}
