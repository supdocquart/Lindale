package timber.log

// DESKTOP: Local stand-in for JakeWharton Timber, which is not on the desktop classpath. A tag is
// kept on the logger instance so tag(...) is thread-safe, and every throwable overload formats its
// message through the same path as the non-throwable one. Keep identical behaviour in the innertube
// shim (innertube/src/commonMain/kotlin/timber/log/Timber.kt).
class Timber private constructor(private val tag: String?) {

    fun d(vararg args: Any?) = write(tag, "DEBUG", args.joinToString(" "))
    fun d(message: String, vararg args: Any?) = write(tag, "DEBUG", format(message, args))
    fun d(t: Throwable, message: String? = null, vararg args: Any?) {
        write(tag, "DEBUG", format(message ?: "", args))
        t.printStackTrace()
    }

    fun e(vararg args: Any?) = write(tag, "ERROR", args.joinToString(" "))
    fun e(message: String, vararg args: Any?) = write(tag, "ERROR", format(message, args))
    fun e(t: Throwable, message: String? = null, vararg args: Any?) {
        write(tag, "ERROR", format(message ?: "", args))
        t.printStackTrace()
    }

    fun w(vararg args: Any?) = write(tag, "WARN", args.joinToString(" "))
    fun w(message: String, vararg args: Any?) = write(tag, "WARN", format(message, args))
    fun w(t: Throwable, message: String? = null, vararg args: Any?) {
        write(tag, "WARN", format(message ?: "", args))
        t.printStackTrace()
    }

    fun i(vararg args: Any?) = write(tag, "INFO", args.joinToString(" "))
    fun i(message: String, vararg args: Any?) = write(tag, "INFO", format(message, args))
    fun i(t: Throwable, message: String? = null, vararg args: Any?) {
        write(tag, "INFO", format(message ?: "", args))
        t.printStackTrace()
    }

    fun v(vararg args: Any?) {}
    fun v(message: String, vararg args: Any?) {}

    companion object {
        fun tag(tag: String): Timber = Timber(tag)

        fun d(vararg args: Any?) = write(null, "DEBUG", args.joinToString(" "))
        fun d(message: String, vararg args: Any?) = write(null, "DEBUG", format(message, args))
        fun d(t: Throwable, message: String? = null, vararg args: Any?) {
            write(null, "DEBUG", format(message ?: "", args))
            t.printStackTrace()
        }

        fun e(vararg args: Any?) = write(null, "ERROR", args.joinToString(" "))
        fun e(message: String, vararg args: Any?) = write(null, "ERROR", format(message, args))
        fun e(t: Throwable, message: String? = null, vararg args: Any?) {
            write(null, "ERROR", format(message ?: "", args))
            t.printStackTrace()
        }

        fun w(vararg args: Any?) = write(null, "WARN", args.joinToString(" "))
        fun w(message: String, vararg args: Any?) = write(null, "WARN", format(message, args))
        fun w(t: Throwable, message: String? = null, vararg args: Any?) {
            write(null, "WARN", format(message ?: "", args))
            t.printStackTrace()
        }

        fun i(vararg args: Any?) = write(null, "INFO", args.joinToString(" "))
        fun i(message: String, vararg args: Any?) = write(null, "INFO", format(message, args))
        fun i(t: Throwable, message: String? = null, vararg args: Any?) {
            write(null, "INFO", format(message ?: "", args))
            t.printStackTrace()
        }

        fun v(vararg args: Any?) {}
        fun v(message: String, vararg args: Any?) {}

        private fun write(tag: String?, level: String, message: String) {
            val line = if (tag == null) "$level: $message" else "$level/$tag: $message"
            if (level == "ERROR") System.err.println(line) else println(line)
        }

        private fun format(message: String, args: Array<out Any?>): String =
            try {
                message.format(*args)
            } catch (_: Throwable) {
                "$message " + args.joinToString(" ")
            }
    }
}
