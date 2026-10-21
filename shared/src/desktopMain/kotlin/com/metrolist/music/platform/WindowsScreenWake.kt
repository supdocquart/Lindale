package com.metrolist.music.platform

import com.sun.jna.Library
import com.sun.jna.Native
import java.util.concurrent.Executors

/** SetThreadExecutionState is per-thread, so every call goes through one dedicated thread. */
internal object WindowsScreenWake {
    private interface Kernel32 : Library {
        fun SetThreadExecutionState(flags: Int): Int
    }

    private const val ES_CONTINUOUS = 0x80000000.toInt()
    private const val ES_SYSTEM_REQUIRED = 0x00000001
    private const val ES_DISPLAY_REQUIRED = 0x00000002

    private val thread = Executors.newSingleThreadExecutor { r ->
        Thread(r, "screen-wake").apply { isDaemon = true }
    }

    private val kernel32: Kernel32? by lazy {
        runCatching { Native.load("kernel32", Kernel32::class.java) }.getOrNull()
    }

    fun install() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        ScreenWake.backend = { keepAwake ->
            thread.execute {
                val flags = if (keepAwake) ES_CONTINUOUS or ES_SYSTEM_REQUIRED or ES_DISPLAY_REQUIRED else ES_CONTINUOUS
                kernel32?.SetThreadExecutionState(flags)
            }
        }
    }
}
