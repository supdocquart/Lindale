package timber.log

object Timber {
    fun tag(tag: String): Timber = this

    fun d(vararg args: Any?) {
        println("DEBUG: " + args.joinToString(" "))
    }
    fun d(message: String, vararg args: Any?) {
        try {
            println("DEBUG: " + message.format(*args))
        } catch (_: Throwable) {
            println("DEBUG: $message " + args.joinToString(" "))
        }
    }
    fun d(t: Throwable, message: String? = null, vararg args: Any?) {
        println("DEBUG: " + (message ?: "") + " " + args.joinToString(" "))
        t.printStackTrace()
    }

    fun e(vararg args: Any?) {
        System.err.println("ERROR: " + args.joinToString(" "))
    }
    fun e(message: String, vararg args: Any?) {
        try {
            System.err.println("ERROR: " + message.format(*args))
        } catch (_: Throwable) {
            System.err.println("ERROR: $message " + args.joinToString(" "))
        }
    }
    fun e(t: Throwable, message: String? = null, vararg args: Any?) {
        System.err.println("ERROR: " + (message ?: "") + " " + args.joinToString(" "))
        t.printStackTrace()
    }

    fun w(vararg args: Any?) {
        println("WARN: " + args.joinToString(" "))
    }
    fun w(message: String, vararg args: Any?) {
        try {
            println("WARN: " + message.format(*args))
        } catch (_: Throwable) {
            println("WARN: $message " + args.joinToString(" "))
        }
    }
    fun w(t: Throwable, message: String? = null, vararg args: Any?) {
        println("WARN: " + (message ?: "") + " " + args.joinToString(" "))
        t.printStackTrace()
    }

    fun i(vararg args: Any?) {
        println("INFO: " + args.joinToString(" "))
    }
    fun i(message: String, vararg args: Any?) {
        try {
            println("INFO: " + message.format(*args))
        } catch (_: Throwable) {
            println("INFO: $message " + args.joinToString(" "))
        }
    }
    fun i(t: Throwable, message: String? = null, vararg args: Any?) {
        println("INFO: " + (message ?: "") + " " + args.joinToString(" "))
        t.printStackTrace()
    }

    fun v(vararg args: Any?) {}
    fun v(message: String, vararg args: Any?) {}
}
