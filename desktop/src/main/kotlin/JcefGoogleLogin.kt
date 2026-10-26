import com.jetbrains.cef.JCefAppConfig
import com.metrolist.music.platform.GoogleLogin
import org.cef.CefApp
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCookieVisitor
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import timber.log.Timber
import java.awt.BorderLayout
import java.awt.EventQueue
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JFrame
import javax.swing.WindowConstants

/**
 * DESKTOP: "Sign in with Google" window built on JetBrains Runtime's JCEF, replacing upstream's
 * Android WebView login. The user signs in on Google's own page; the app only reads the
 * music.youtube.com cookies and ytcfg values afterwards, exactly like upstream's extractAuthData.
 * The CEF profile is in-memory (empty cache path), so no Google session is written to disk.
 * Never log cookie values, tokens or e-mail addresses here.
 */
object JcefGoogleLogin {
    private const val TAG = "GoogleLogin"
    private const val LOGIN_URL =
        "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
            "&continue=https%3A%2F%2Fmusic.youtube.com%2F"
    private const val MUSIC_URL = "https://music.youtube.com"
    private const val MARKER = "METROLIST_LOGIN:"

    // Same values upstream reads through its JavaScript interface.
    private const val READ_CONFIG_JS =
        "(function(){try{var c=(window.yt&&window.yt.config_)||{};" +
            "console.log('$MARKER'+JSON.stringify({v:c.VISITOR_DATA||'',d:c.DATASYNC_ID||''," +
            "u:String(c.SESSION_INDEX||0)}));}catch(e){console.log('$MARKER{}');}})();"

    private var cefApp: CefApp? = null

    fun install() {
        val available = runCatching {
            Class.forName("org.cef.CefApp")
            Class.forName("com.jetbrains.cef.JCefAppConfig")
        }.isSuccess
        if (!available) {
            Timber.tag(TAG).i("JCEF not available; only cookie paste login")
            return
        }
        GoogleLogin.opener = { onResult -> EventQueue.invokeLater { open(onResult) } }
    }

    private fun app(): CefApp {
        cefApp?.let { return it }
        val config = JCefAppConfig.getInstance()
        val args = config.appArgs
        val settings = config.cefSettings
        // DESKTOP: in-memory profile; cookies vanish when the app exits.
        settings.cache_path = ""
        settings.persist_session_cookies = false
        CefApp.startup(args)
        return CefApp.getInstance(args, settings).also { cefApp = it }
    }

    private fun open(onResult: (GoogleLogin.Result) -> Unit) {
        val app = try {
            app()
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "JCEF init failed")
            onResult(GoogleLogin.Result.Failed("embedded browser could not start"))
            return
        }
        // DESKTOP: every sign-in starts from an empty session.
        runCatching { CefCookieManager.getGlobalManager()?.deleteCookies("", "") }

        val client = app.createClient()
        val browser = client.createBrowser(LOGIN_URL, false, false)
        val frame = JFrame("Sign in with Google")
        val finished = AtomicBoolean(false)

        fun finish(result: GoogleLogin.Result) {
            if (!finished.compareAndSet(false, true)) return
            EventQueue.invokeLater {
                runCatching { frame.dispose() }
                runCatching { browser.close(true) }
                runCatching { client.dispose() }
            }
            Timber.tag(TAG).i("login window finished: %s", result::class.simpleName)
            onResult(result)
        }

        fun readCookiesThenFinish(visitorData: String, dataSyncId: String, authUser: String) {
            val cookies = Collections.synchronizedList(mutableListOf<String>())
            val started = runCatching {
                CefCookieManager.getGlobalManager().visitUrlCookies(MUSIC_URL, true, object : CefCookieVisitor {
                    override fun visit(cookie: CefCookie, count: Int, total: Int, delete: BoolRef): Boolean {
                        cookies += "${cookie.name}=${cookie.value}"
                        return true
                    }
                })
            }.getOrDefault(false)
            if (!started) return
            // The visitor runs asynchronously and is not called at all when there are no cookies.
            Thread {
                Thread.sleep(700)
                val names = cookies.map { it.substringBefore('=') }
                if ("SAPISID" in names) {
                    finish(
                        GoogleLogin.Result.Success(
                            cookie = cookies.joinToString("; "),
                            visitorData = visitorData,
                            dataSyncId = dataSyncId.substringBefore("||"),
                            authUser = authUser.filter(Char::isDigit).ifBlank { "0" },
                        ),
                    )
                } else {
                    Timber.tag(TAG).d("on music.youtube.com but not signed in yet (%d cookies)", cookies.size)
                }
            }.apply { isDaemon = true }.start()
        }

        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(
                browser: CefBrowser?,
                level: CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int,
            ): Boolean {
                if (message == null || !message.startsWith(MARKER)) return false
                val json = message.removePrefix(MARKER)
                fun field(key: String) = Regex("\"$key\":\"([^\"]*)\"").find(json)?.groupValues?.get(1).orEmpty()
                readCookiesThenFinish(field("v"), field("d"), field("u"))
                return true
            }
        })

        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, cefFrame: CefFrame, httpStatusCode: Int) {
                if (!cefFrame.isMain) return
                val url = browser.url.orEmpty()
                if ("/signin/rejected" in url) {
                    Timber.tag(TAG).w("Google rejected the embedded browser: %s", url.substringBefore('?'))
                    finish(GoogleLogin.Result.Blocked)
                    return
                }
                if (url.startsWith(MUSIC_URL)) browser.executeJavaScript(READ_CONFIG_JS, url, 0)
            }
        })

        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) = finish(GoogleLogin.Result.Cancelled)
        })
        frame.contentPane.layout = BorderLayout()
        frame.contentPane.add(browser.uiComponent, BorderLayout.CENTER)
        frame.setSize(520, 760)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}
