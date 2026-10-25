package com.metrolist.music.platform

import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import timber.log.Timber
import java.lang.management.BufferPoolMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import kotlin.concurrent.thread

/** Logs a one-line memory breakdown of this process every minute (tag [MEM]). */
internal object MemoryMonitor {
    @Structure.FieldOrder(
        "cb", "PageFaultCount", "PeakWorkingSetSize", "WorkingSetSize",
        "QuotaPeakPagedPoolUsage", "QuotaPagedPoolUsage", "QuotaPeakNonPagedPoolUsage",
        "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage", "PrivateUsage",
    )
    class Counters : Structure() {
        @JvmField var cb: Int = 0
        @JvmField var PageFaultCount: Int = 0
        @JvmField var PeakWorkingSetSize: Long = 0
        @JvmField var WorkingSetSize: Long = 0
        @JvmField var QuotaPeakPagedPoolUsage: Long = 0
        @JvmField var QuotaPagedPoolUsage: Long = 0
        @JvmField var QuotaPeakNonPagedPoolUsage: Long = 0
        @JvmField var QuotaNonPagedPoolUsage: Long = 0
        @JvmField var PagefileUsage: Long = 0
        @JvmField var PeakPagefileUsage: Long = 0
        @JvmField var PrivateUsage: Long = 0
    }

    @Suppress("FunctionName")
    private interface Kernel32 : Library {
        fun GetCurrentProcess(): Pointer
        fun K32GetProcessMemoryInfo(process: Pointer, counters: Counters, cb: Int): Boolean
    }

    private val kernel32: Kernel32? by lazy {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) null
        else runCatching { Native.load("Kernel32", Kernel32::class.java) }.getOrNull()
    }

    private const val MB = 1024L * 1024L

    // DESKTOP: only the first NMT summary is logged raw, it is meant to document the format once.
    @Volatile
    private var rawNmtSummaryLogged = false

    fun start(intervalMs: Long = 60_000L) {
        thread(name = "memory-monitor", isDaemon = true) {
            Thread.sleep(15_000L)
            var tick = 0
            while (true) {
                runCatching { Timber.i(snapshot()) }
                if (tick++ % 5 == 0) runCatching { Timber.i(jvmNativeBreakdown()) }
                Thread.sleep(intervalMs)
            }
        }
    }

    /** Per-category committed memory from NativeMemoryTracking, e.g. "[NMT] GC=12MB Compiler=3MB ...". */
    private fun jvmNativeBreakdown(): String {
        val out = nmtSummary()
        if (out == null) return "[NMT] unavailable"
        if (!rawNmtSummaryLogged) {
            rawNmtSummaryLogged = true
            // DESKTOP: log the raw summary once so the real NMT output format can be verified.
            Timber.d("[NMT] raw=${out.take(300)}")
        }
        // DESKTOP: tolerant of thousand separators and extra spaces in the categories/values.
        val parts = Regex("""-\s+([A-Za-z][A-Za-z0-9 _/]*?)\s*\(\s*reserved=\s*[\d,]+KB\s*,\s*committed=\s*([\d,]+)KB""")
            .findAll(out)
            .mapNotNull {
                val name = it.groupValues[1].trim().replace(Regex("""\s+"""), "_")
                val kb = it.groupValues[2].replace(",", "").toLongOrNull() ?: return@mapNotNull null
                name to kb / 1024
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .joinToString(" ") { "${it.first}=${it.second}MB" }
        // DESKTOP: print an explicit marker instead of a blank line when there is nothing to report.
        return if (parts.isEmpty()) "[NMT] unavailable" else "[NMT] $parts"
    }

    private fun nmtSummary(): String? = runCatching {
        val server = ManagementFactory.getPlatformMBeanServer()
        val name = javax.management.ObjectName("com.sun.management:type=DiagnosticCommand")
        server.invoke(name, "vmNativeMemory", arrayOf<Any>(arrayOf("summary")), arrayOf(Array<String>::class.java.name)) as String
    }.getOrNull()

    fun snapshot(): String {
        val sb = StringBuilder("[MEM]")
        kernel32?.let { k ->
            val c = Counters()
            c.cb = c.size()
            if (k.K32GetProcessMemoryInfo(k.GetCurrentProcess(), c, c.cb)) {
                sb.append(" workingSet=").append(c.WorkingSetSize / MB).append("MB")
                sb.append(" private=").append(c.PrivateUsage / MB).append("MB")
                sb.append(" peakWS=").append(c.PeakWorkingSetSize / MB).append("MB")
            }
        }
        val rt = Runtime.getRuntime()
        sb.append(" heapUsed=").append((rt.totalMemory() - rt.freeMemory()) / MB).append("MB")
        sb.append(" heapCommitted=").append(rt.totalMemory() / MB).append("MB")
        sb.append(" heapMax=").append(rt.maxMemory() / MB).append("MB")
        for (pool in ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.type != MemoryType.NON_HEAP) continue
            val name = when {
                pool.name.contains("Metaspace") && !pool.name.contains("Compressed") -> "metaspace"
                pool.name.contains("CodeHeap") || pool.name.contains("CodeCache") || pool.name.contains("Code Cache") -> "code"
                else -> continue
            }
            sb.append(' ').append(name).append('=').append(pool.usage.committed / MB).append("MB")
        }
        ManagementFactory.getPlatformMXBeans(BufferPoolMXBean::class.java)
            .firstOrNull { it.name == "direct" }
            ?.let { sb.append(" direct=").append(it.memoryUsed / MB).append("MB") }
        runCatching { SingletonImageLoader.get(PlatformContext.INSTANCE).memoryCache }.getOrNull()?.let {
            sb.append(" imageCache=").append(it.size / MB).append('/').append(it.maxSize / MB).append("MB")
        }
        sb.append(" threads=").append(Thread.activeCount())
        jvmNativeCommittedMb()?.let { sb.append(" jvmNative=").append(it).append("MB") }
        sb.append(" render=").append(System.getProperty("skiko.renderApi") ?: "default")
        return sb.toString()
    }

    /** Total memory the JVM itself has committed (heap, classes, threads, code, GC); needs NativeMemoryTracking. */
    private fun jvmNativeCommittedMb(): Long? = runCatching {
        val out = nmtSummary() ?: return null
        // DESKTOP: same tolerance as jvmNativeBreakdown (thousand separators and extra spaces).
        Regex("""Total:\s*reserved=\s*[\d,]+KB\s*,\s*committed=\s*([\d,]+)KB""")
            .find(out)?.groupValues?.get(1)?.replace(",", "")?.toLong()?.div(1024)
    }.getOrNull()
}
