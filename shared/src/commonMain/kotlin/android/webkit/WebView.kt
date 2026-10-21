package android.webkit

class WebSettings {
    var javaScriptEnabled: Boolean = false
    var domStorageEnabled: Boolean = false
    fun setSupportZoom(support: Boolean) {}
    var builtInZoomControls: Boolean = false
    var displayZoomControls: Boolean = false
}

open class WebViewClient {
    open fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
    open fun onPageFinished(view: WebView, url: String?) {}
}

class WebResourceRequest(val url: android.net.Uri = android.net.Uri())

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class JavascriptInterface

class CookieManager {
    fun flush() {}
    fun getCookie(url: String): String? = null
    fun setCookie(url: String, value: String) {}
    fun removeAllCookies(callback: Any?) {}
    fun setAcceptCookie(accept: Boolean) {}

    companion object {
        private val instance = CookieManager()
        fun getInstance(): CookieManager = instance
    }
}

open class WebView(context: Any? = null) {
    var webViewClient: WebViewClient? = null
    val settings: WebSettings = WebSettings()
    fun addJavascriptInterface(obj: Any, name: String) {}
    fun loadUrl(url: String) {}
    fun stopLoading() {}
    fun clearHistory() {}
    fun clearCache(includeDiskFiles: Boolean) {}
    fun clearFormData() {}
    fun evaluateJavascript(script: String, resultCallback: ((String) -> Unit)? = null) {}
    fun reload() {}
    fun canGoBack(): Boolean = false
    fun goBack() {}
}
