package android.content

import android.net.Uri

class Intent(
    var action: String? = null,
    var data: Uri? = null
) {
    var type: String? = null
    constructor(packageContext: Any?, cls: Any?) : this()

    var component: Any? = null
    var selector: Any? = null

    private val extras = mutableMapOf<String, Any?>()

    fun putExtra(name: String, value: Any?): Intent { extras[name] = value; return this }
    fun setType(type: String): Intent { this.type = type; return this }
    fun addFlags(flags: Int): Intent = this
    fun addCategory(category: String): Intent = this
    fun setData(data: Uri?): Intent { this.data = data; return this }
    fun setDataAndType(data: Uri?, type: String?): Intent { this.data = data; this.type = type; return this }
    fun setPackage(packageName: String?): Intent = this
    fun setAction(action: String?): Intent { this.action = action; return this }

    fun getParcelableExtra(name: String): Any? = extras[name]
    fun getSerializableExtra(name: String): Any? = extras[name]
    fun getStringExtra(name: String): String? = extras[name] as? String
    fun getLongExtra(name: String, defaultValue: Long): Long = (extras[name] as? Number)?.toLong() ?: defaultValue
    fun getIntExtra(name: String, defaultValue: Int): Int = (extras[name] as? Number)?.toInt() ?: defaultValue
    fun getBooleanExtra(name: String, defaultValue: Boolean): Boolean = (extras[name] as? Boolean) ?: defaultValue

    fun resolveActivity(packageManager: Any?): Any? = null

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val ACTION_WEB_SEARCH = "android.intent.action.WEB_SEARCH"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE"
        const val FLAG_GRANT_READ_URI_PERMISSION = 1
        const val FLAG_GRANT_WRITE_URI_PERMISSION = 2
        const val FLAG_ACTIVITY_NEW_TASK = 268435456
        const val FLAG_ACTIVITY_CLEAR_TASK = 32768
        const val URI_INTENT_SCHEME = 1
        const val ACTION_APP_OPEN_BY_DEFAULT_SETTINGS = "android.settings.action.APP_OPEN_BY_DEFAULT_SETTINGS"
        const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
        const val EXTRA_PACKAGE_NAME = "android.provider.extra.APP_PACKAGE"

        fun createChooser(target: Intent, title: CharSequence?): Intent = target
        fun parseUri(uri: String, flags: Int): Intent = Intent(ACTION_VIEW, Uri.parse(uri))
    }
}
