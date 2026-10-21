package android.graphics

class Paint {
    var isAntiAlias: Boolean = false
    var maskFilter: Any? = null
    var alpha: Int = 255
    var color: Int = 0
    var textSize: Float = 0f
    var typeface: Any? = null
    fun setShadowLayer(radius: Float, dx: Float, dy: Float, color: Int) {}
}

class BlurMaskFilter(val radius: Float, val style: Any) {
    enum class Blur { NORMAL, SOLID, OUTER, INNER }
}

object Typeface {
    const val DEFAULT = 0
    const val BOLD = 1
    const val ITALIC = 2
    fun create(family: Any?, style: Int): Any? = null
}
