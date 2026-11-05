package com.metrolist.music.ui.utils

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * DESKTOP: Material3 pull-to-refresh is driven by nested-scroll overscroll and resets on fling.
 * Scrolling up past the top with a mouse wheel feeds it overscroll but never flings, so the
 * indicator stayed stuck half-pulled at the top of the screen. This keeps wheel overscroll away
 * from the refresh container; touch drags still reach it.
 *
 * Put it between the pull-to-refresh container and the scrolling list: after
 * `Modifier.pullToRefresh(...)` in the same chain, or on the content of a `PullToRefreshBox`.
 */
fun Modifier.ignoreWheelPullToRefresh(): Modifier = composed {
    val guard = remember { WheelGuard() }
    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Scroll) guard.lastWheelMs = System.currentTimeMillis()
                }
            }
        }
        .nestedScroll(guard)
}

private class WheelGuard : NestedScrollConnection {
    @Volatile var lastWheelMs = 0L

    // Wheel scrolling is animated, so its deltas keep arriving for a short while after the event.
    private fun wheelActive() = System.currentTimeMillis() - lastWheelMs < WHEEL_WINDOW_MS

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y > 0f && wheelActive()) Offset(0f, available.y) else Offset.Zero

    private companion object {
        const val WHEEL_WINDOW_MS = 500L
    }
}
