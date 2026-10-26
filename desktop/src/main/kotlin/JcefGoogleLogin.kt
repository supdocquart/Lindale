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

    const val HELPER_FLAG = "--google-login-helper"
    private const val LANG_PREFIX = "--ui-lang="
    private const val RESULT_PREFIX = "ML_LOGIN_RESULT|"

    fun install() {
        val available = runCatching {
            Class.forName("org.cef.CefApp")
            Class.forName("com.jetbrains.cef.JCefAppConfig")
        }.isSuccess
        if (!available) {
            Timber.tag(TAG).i("JCEF not available; only cookie paste login")
            return
        }
        // DESKTOP: JCEF runs in a child process; a native browser crash then only ends that process.
        GoogleLogin.opener = { onResult -> Thread({ runInChildProcess(onResult) }, "google-login").start() }
    }

    /**
     * Command for the sign-in child: the installed jpackage launcher when there is one, otherwise this
     * JVM rebuilt from its system properties. ProcessHandle.Info.arguments() is empty on Windows, so the
     * current command line cannot simply be replayed.
     */
    private fun childCommand(): List<String>? {
        val extra = listOf(HELPER_FLAG, LANG_PREFIX + java.util.Locale.getDefault().toLanguageTag())
        System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }?.let { path ->
            if (java.io.File(path).isFile) return listOf(path) + extra
        }
        val javaHome = System.getProperty("java.home") ?: return null
        val exe = listOf("bin/java.exe", "bin/java").map { java.io.File(javaHome, it) }.firstOrNull { it.isFile }
            ?: return null
        val classPath = System.getProperty("java.class.path")?.takeIf { it.isNotBlank() } ?: return null
        val mainClass = System.getProperty("sun.java.command")?.substringBefore(' ')?.takeIf { it.isNotBlank() }
            ?: "MainKt"
        // DESKTOP: keep the JVM options except CDS (the child must not rewrite the app's archive) and agents.
        val jvmOptions = runCatching { java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments }
            .getOrDefault(emptyList())
            .filterNot {
                it.startsWith("-XX:SharedArchiveFile") || it.contains("AutoCreateSharedArchive") ||
                    it.startsWith("-agentlib") || it.startsWith("-javaagent")
            }
        return listOf(exe.absolutePath) + jvmOptions + listOf("-cp", classPath, mainClass) + extra
    }

    private fun runInChildProcess(onResult: (GoogleLogin.Result) -> Unit) {
        val command = childCommand()
        if (command == null) {
            onResult(GoogleLogin.Result.Failed("cannot start the sign-in window"))
            return
        }
        Timber.tag(TAG).i("starting sign-in process: %s", command.first())
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "sign-in process could not start")
            onResult(GoogleLogin.Result.Failed("cannot start the sign-in window"))
            return
        }
        var result: GoogleLogin.Result? = null
        var lastLine = ""
        process.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.startsWith(RESULT_PREFIX)) {
                    result = decode(line.removePrefix(RESULT_PREFIX))
                } else if (line.isNotBlank()) {
                    // The child never prints cookies outside the result line.
                    Timber.tag(TAG).i("child: %s", line.take(300))
                    lastLine = line.trim().take(160)
                }
            }
        }
        val exit = runCatching { process.waitFor() }.getOrDefault(-1)
        Timber.tag(TAG).i("sign-in process exited with %d", exit)
        val detail = if (lastLine.isNotEmpty()) ": $lastLine" else ""
        onResult(result ?: GoogleLogin.Result.Failed("sign-in window closed unexpectedly (exit $exit)$detail"))
    }

    /** Entry point of the child process started with [HELPER_FLAG]. */
    fun runHelperProcess(args: Array<String>) {
        System.setProperty("sun.awt.noerasebackground", "true")
        args.firstOrNull { it.startsWith(LANG_PREFIX) }?.removePrefix(LANG_PREFIX)?.takeIf { it.isNotBlank() }
            ?.let { java.util.Locale.setDefault(java.util.Locale.forLanguageTag(it)) }
        EventQueue.invokeLater {
            open { result ->
                println(RESULT_PREFIX + encode(result))
                System.out.flush()
                // CEF shutdown can hang; the result is already delivered, so leave immediately.
                Runtime.getRuntime().halt(0)
            }
        }
    }

    private fun b64(text: String) = java.util.Base64.getEncoder().encodeToString(text.toByteArray())
    private fun unb64(text: String) = String(java.util.Base64.getDecoder().decode(text))

    private fun encode(result: GoogleLogin.Result): String = when (result) {
        is GoogleLogin.Result.Success ->
            listOf("SUCCESS", b64(result.cookie), b64(result.visitorData), b64(result.dataSyncId), b64(result.authUser))
                .joinToString("|")
        GoogleLogin.Result.Blocked -> "BLOCKED"
        GoogleLogin.Result.Cancelled -> "CANCELLED"
        is GoogleLogin.Result.Failed -> "FAILED|" + b64(result.reason)
    }

    private fun decode(text: String): GoogleLogin.Result = runCatching {
        val parts = text.split("|")
        when (parts[0]) {
            "SUCCESS" -> GoogleLogin.Result.Success(unb64(parts[1]), unb64(parts[2]), unb64(parts[3]), unb64(parts[4]))
            "BLOCKED" -> GoogleLogin.Result.Blocked
            "CANCELLED" -> GoogleLogin.Result.Cancelled
            else -> GoogleLogin.Result.Failed(parts.getOrNull(1)?.let(::unb64) ?: "unknown")
        }
    }.getOrDefault(GoogleLogin.Result.Failed("bad result from the sign-in window"))

    private fun app(): CefApp {
        cefApp?.let { return it }
        val config = JCefAppConfig.getInstance()
        val args = config.appArgs
        val settings = config.cefSettings
        // DESKTOP: a normal (windowed) browser inside a JFrame; JBR may default to off-screen rendering.
        settings.windowless_rendering_enabled = false
        // DESKTOP: in-memory profile; cookies vanish when the app exits.
        settings.cache_path = ""
        settings.persist_session_cookies = false
        Timber.tag(TAG).d("CefApp.startup")
        CefApp.startup(args)
        return CefApp.getInstance(args, settings).also {
            cefApp = it
            Timber.tag(TAG).d("CefApp ready")
        }
    }

    private fun open(onResult: (GoogleLogin.Result) -> Unit) {
        try {
            openUnsafe(onResult)
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "sign-in window failed")
            onResult(GoogleLogin.Result.Failed(t.javaClass.simpleName))
        }
    }

    private fun openUnsafe(onResult: (GoogleLogin.Result) -> Unit) {
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
        val title = runCatching {
            kotlinx.coroutines.runBlocking { GoogleLogin.windowTitle() }
        }.getOrDefault("Google")
        val frame = JFrame(title)
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
