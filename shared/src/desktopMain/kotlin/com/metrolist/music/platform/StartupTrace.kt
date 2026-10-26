package com.metrolist.music.platform

import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * DESKTOP: logs `[STARTUP] <name> +<ms since JVM start>` once per mark, so cold starts can be compared
 * from the log alone. Time is measured from the JVM's own start time, not from main().
 */
object StartupTrace {
    private val jvmStart: Long = runCatching { ManagementFactory.getRuntimeMXBean().startTime }
        .getOrDefault(System.currentTimeMillis())
    private val seen = ConcurrentHashMap.newKeySet<String>()

    fun mark(name: String) {
        if (!seen.add(name)) return
        println("[STARTUP] $name +${System.currentTimeMillis() - jvmStart}ms")
    }

    /** Runs [block] and logs its duration as `<name> took <ms>`. */
    fun <T> span(name: String, block: () -> T): T {
        val started = System.currentTimeMillis()
        try {
            return block()
        } finally {
            println("[STARTUP] $name took ${System.currentTimeMillis() - started}ms")
        }
    }
}
