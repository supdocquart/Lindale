package android.app

open class Activity {
    val window: android.view.Window = android.view.Window()

    companion object {
        const val RESULT_OK = -1
    }
    
    fun finishAndRemoveTask() {}
}
