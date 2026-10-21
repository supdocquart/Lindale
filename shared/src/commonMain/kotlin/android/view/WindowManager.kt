package android.view

class Window {
    val decorView: Any = Any()
    fun addFlags(flags: Int) {}
    fun clearFlags(flags: Int) {}
}

object WindowManager {
    object LayoutParams {
        const val FLAG_KEEP_SCREEN_ON = 128
    }
}
