// DESKTOP: Remembers the main window geometry between runs (upstream is Android-only).
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.metrolist.music.platform.KeyValueStore
import java.awt.GraphicsEnvironment

object WindowStateStore {
    private const val STORE_NAME = "window"
    private const val KEY_WIDTH = "width"
    private const val KEY_HEIGHT = "height"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"
    private const val KEY_MAXIMIZED = "maximized"

    private const val DEFAULT_WIDTH = 1280f
    private const val DEFAULT_HEIGHT = 800f
    private const val MIN_WIDTH = 800f
    private const val MIN_HEIGHT = 560f

    fun load(): WindowState {
        val prefs = runCatching { KeyValueStore.get(STORE_NAME) }.getOrNull()
            ?: return defaultState()

        val width = prefs.getString(KEY_WIDTH, null)?.toFloatOrNull()?.takeIf { it > 0f }
        val height = prefs.getString(KEY_HEIGHT, null)?.toFloatOrNull()?.takeIf { it > 0f }
        val size = DpSize(
            (width ?: DEFAULT_WIDTH).coerceAtLeast(MIN_WIDTH).dp,
            (height ?: DEFAULT_HEIGHT).coerceAtLeast(MIN_HEIGHT).dp,
        )

        val x = prefs.getString(KEY_X, null)?.toFloatOrNull()
        val y = prefs.getString(KEY_Y, null)?.toFloatOrNull()
        val position = if (x != null && y != null && isOnScreen(x, y)) {
            WindowPosition(x.dp, y.dp)
        } else {
            WindowPosition.PlatformDefault
        }

        val maximized = prefs.getString(KEY_MAXIMIZED, null)?.toBooleanStrictOrNull() ?: false
        return WindowState(
            placement = if (maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = position,
            size = size,
        )
    }

    fun save(state: WindowState) {
        runCatching {
            val prefs = KeyValueStore.get(STORE_NAME)
            prefs.edit {
                putString(KEY_MAXIMIZED, (state.placement == WindowPlacement.Maximized).toString())
                // DESKTOP: Keep the floating geometry so restoring from maximized returns to it.
                if (state.placement == WindowPlacement.Floating) {
                    putString(KEY_WIDTH, state.size.width.value.toString())
                    putString(KEY_HEIGHT, state.size.height.value.toString())
                    val position = state.position
                    if (position is WindowPosition.Absolute) {
                        putString(KEY_X, position.x.value.toString())
                        putString(KEY_Y, position.y.value.toString())
                    }
                }
            }
        }
    }

    private fun defaultState() = WindowState(
        placement = WindowPlacement.Floating,
        position = WindowPosition.PlatformDefault,
        size = DpSize(DEFAULT_WIDTH.dp, DEFAULT_HEIGHT.dp),
    )

    private fun isOnScreen(x: Float, y: Float): Boolean = runCatching {
        val point = java.awt.Point(x.toInt(), y.toInt())
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { device ->
            device.defaultConfiguration.bounds.contains(point)
        }
    }.getOrDefault(false)
}
