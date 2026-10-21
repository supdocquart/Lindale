package android.content.res

class Configuration(
    var orientation: Int = ORIENTATION_PORTRAIT,
    var screenWidthDp: Int = 1200,
    var screenHeightDp: Int = 800,
) {
    companion object {
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
        const val ORIENTATION_UNDEFINED = 0
    }
}
