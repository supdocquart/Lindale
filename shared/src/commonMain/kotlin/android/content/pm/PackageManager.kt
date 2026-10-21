package android.content.pm

import android.content.Intent

open class PackageManager {
    fun getLaunchIntentForPackage(packageName: String): Intent? = null
    fun getPackageInfo(packageName: String, flags: Int): Any? = null

    companion object : PackageManager() {
        const val PERMISSION_GRANTED = 0
        const val PERMISSION_DENIED = -1
    }
}
