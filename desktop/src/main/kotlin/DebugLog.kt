import com.metrolist.music.utils.UiTrace
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.accessibility.Accessible
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleState
import javax.swing.SwingUtilities
import com.metrolist.music.platform.AppBrand

/**
 * Writes stdout/stderr, crashes, navigation, clicks and key presses to logs/latest.log.
 * Disable with -Dmetrolist.trace=false; disable element lookup with -Dmetrolist.trace.a11y=false.
 */
object DebugLog {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val lock = Any()
    private var file: PrintStream? = null

    fun install() {
        if (System.getProperty("metrolist.trace") == "false" || file != null) return
        runCatching {
            val packaged = System.getProperty("compose.application.resources.dir") != null
            val dir = when {
                System.getProperty("metrolist.logDir") != null -> File(System.getProperty("metrolist.logDir"))
                // Installed apps cannot write next to the exe; keep logs in the user's profile.
                packaged -> File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "${AppBrand.NAME}/logs")
                else -> File(System.getProperty("user.dir"), "logs")
            }
            dir.mkdirs()
            val latest = File(dir, "latest.log")
            if (latest.exists()) {
                val previous = File(dir, "previous.log")
                previous.delete()
                latest.renameTo(previous)
            }
            file = PrintStream(FileOutputStream(latest, false), true, Charsets.UTF_8)

            write("START", "time=${LocalDateTime.now()} java=${System.getProperty("java.version")} vendor=${System.getProperty("java.vendor")} vm=${System.getProperty("java.vm.name")} os=${System.getProperty("os.name")} log=${latest.absolutePath}")

            val outCs = runCatching { System.out.charset() }.getOrDefault(Charset.defaultCharset())
            val errCs = runCatching { System.err.charset() }.getOrDefault(Charset.defaultCharset())
            System.setOut(PrintStream(TeeStream(System.out, "OUT", outCs), true, outCs))
            System.setErr(PrintStream(TeeStream(System.err, "ERR", errCs), true, errCs))

            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                write("CRASH", "thread=${t.name} page=${UiTrace.currentRoute}\n" + e.stackTraceToString())
                previousHandler?.uncaughtException(t, e)
            }

            Toolkit.getDefaultToolkit().addAWTEventListener({ ev ->
                runCatching {
                    when {
                        ev is MouseEvent && ev.id == MouseEvent.MOUSE_PRESSED -> onMouse(ev)
                        ev is KeyEvent && ev.id == KeyEvent.KEY_PRESSED -> onKey(ev)
                    }
                }
            }, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.KEY_EVENT_MASK)

            Runtime.getRuntime().addShutdownHook(Thread { write("EXIT", "page=${UiTrace.currentRoute}") })
        }.onFailure { it.printStackTrace() }
    }

    fun write(tag: String, message: String) {
        val f = file ?: return
        synchronized(lock) {
            f.println("${LocalDateTime.now().format(timeFmt)} [$tag] $message")
        }
    }

    private fun onMouse(ev: MouseEvent) {
        val src = ev.component ?: return
        val win: Window? = src as? Window ?: SwingUtilities.getWindowAncestor(src)
        val p = if (win != null) SwingUtilities.convertPoint(src, ev.point, win) else ev.point
        val button = when (ev.button) {
            MouseEvent.BUTTON1 -> "left"
            MouseEvent.BUTTON2 -> "middle"
            MouseEvent.BUTTON3 -> "right"
            else -> "b${ev.button}"
        }
        val size = win?.let { "${it.width}x${it.height}" } ?: "?"
        val target = if (System.getProperty("metrolist.trace.a11y") == "false") "-" else describeAt(src, ev.point)
        write(
            "CLICK",
            "$button x=${p.x} y=${p.y} win=$size count=${ev.clickCount} page=${UiTrace.currentRoute} player=${UiTrace.playerSheet} target=$target",
        )
    }

    private fun onKey(ev: KeyEvent) {
        val mods = KeyEvent.getModifiersExText(ev.modifiersEx)
        val key = KeyEvent.getKeyText(ev.keyCode)
        val printable = ev.keyChar != KeyEvent.CHAR_UNDEFINED && !ev.keyChar.isISOControl() &&
            (ev.modifiersEx and (KeyEvent.CTRL_DOWN_MASK or KeyEvent.ALT_DOWN_MASK or KeyEvent.META_DOWN_MASK)) == 0
        // Plain typing is not recorded verbatim so cookies/passwords never land in the log.
        val shown = if (printable) "<typing>" else if (mods.isNotEmpty()) "$mods+$key" else key
        write("KEY", "$shown page=${UiTrace.currentRoute}")
    }

    private fun describeAt(src: Component, point: Point): String = runCatching {
        val deepest = SwingUtilities.getDeepestComponentAt(src, point.x, point.y) ?: src
        var local = SwingUtilities.convertPoint(src, point, deepest)
        var ctx = deepest.accessibleContext
        val parts = ArrayList<String>()
        var depth = 0
        while (ctx != null && depth++ < 40) {
            val name = ctx.accessibleName?.trim()?.takeIf { it.isNotEmpty() }
            val desc = ctx.accessibleDescription?.trim()?.takeIf { it.isNotEmpty() && it != name }
            // DESKTOP: text inputs only log their role, never name/description, because the accessible
            // name of a field can be the text typed or pasted into it (cookies, tokens).
            val textInput = ctx.accessibleRole == AccessibleRole.TEXT ||
                ctx.accessibleRole == AccessibleRole.PASSWORD_TEXT ||
                ctx.accessibleEditableText != null ||
                ctx.accessibleStateSet?.contains(AccessibleState.EDITABLE) == true
            if (name != null || desc != null) {
                val role = ctx.accessibleRole?.toDisplayString()
                parts += if (textInput) {
                    listOfNotNull(role, "<text>").joinToString(":").take(80)
                } else {
                    listOfNotNull(role, name, desc).joinToString(":").take(80)
                }
            }
            val comp = ctx.accessibleComponent ?: break
            val child: Accessible = comp.getAccessibleAt(local) ?: break
            val childCtx = child.accessibleContext ?: break
            if (childCtx === ctx) break
            childCtx.accessibleComponent?.location?.let { local = Point(local.x - it.x, local.y - it.y) }
            ctx = childCtx
        }
        if (parts.isEmpty()) deepest.javaClass.simpleName else parts.takeLast(3).joinToString(" > ")
    }.getOrElse { "?(${it.javaClass.simpleName})" }

    private class TeeStream(
        private val original: OutputStream,
        private val tag: String,
        private val charset: Charset,
    ) : OutputStream() {
        private val buffer = ByteArrayOutputStream()

        override fun write(b: Int) {
            synchronized(this) {
                original.write(b)
                if (b == '\n'.code) flushLine() else buffer.write(b)
            }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            synchronized(this) {
                original.write(b, off, len)
                for (i in off until off + len) {
                    if (b[i] == '\n'.code.toByte()) flushLine() else buffer.write(b[i].toInt())
                }
            }
        }

        override fun flush() = original.flush()

        private fun flushLine() {
            val line = buffer.toString(charset).trimEnd('\r')
            buffer.reset()
            write(tag, line)
        }

        private fun write(tag: String, line: String) = DebugLog.write(tag, line)
    }
}
