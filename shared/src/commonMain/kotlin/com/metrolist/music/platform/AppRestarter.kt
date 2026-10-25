package com.metrolist.music.platform

import java.io.File
import timber.log.Timber

/**
 * Relaunches the desktop app so a restored database and settings are picked up.
 *
 * Installed builds run through a jpackage launcher whose full path is exposed as the
 * `jpackage.app-path` system property. Development builds run as a plain JVM process, so the
 * current command line is reused instead. The current process always exits afterwards; if the
 * relaunch cannot be started the app simply closes, matching the previous behaviour.
 */
object AppRestarter {
    private const val TAG = "AppRestarter"
    private const val JPACKAGE_APP_PATH = "jpackage.app-path"
    private const val CHILD_STARTUP_GRACE_MS = 1_500L

    fun restart(): Nothing {
        try {
            val launcher = System.getProperty(JPACKAGE_APP_PATH)
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)

            val child = if (launcher != null && launcher.isFile) {
                // DESKTOP: log the executable path only; the arguments may contain tokens or user data.
                Timber.tag(TAG).i("Relaunching via jpackage launcher: %s", launcher.absolutePath)
                ProcessBuilder(launcher.absolutePath).start()
            } else {
                // DESKTOP: dev builds are started by Gradle, so replay the running java command.
                val info = ProcessHandle.current().info()
                val command = info.command().orElse(null)
                val arguments = info.arguments().orElse(null)
                if (command != null && arguments != null) {
                    Timber.tag(TAG).i("Relaunching via replayed java command: %s", command)
                    ProcessBuilder(listOf(command) + arguments).start()
                } else {
                    Timber.tag(TAG).w(
                        "Cannot relaunch the app: command=%s, arguments=%s",
                        command,
                        arguments?.size,
                    )
                    null
                }
            }

            // DESKTOP: a child that dies immediately (locked files, bad working directory) would
            // otherwise exit silently; wait briefly and log the outcome before exiting ourselves.
            child?.let(::logChildStartup)
        } catch (e: Throwable) {
            // A relaunch failure must never prevent the process from exiting.
            Timber.tag(TAG).e(e, "Failed to relaunch the app")
        }

        Runtime.getRuntime().exit(0)
        // Runtime.exit never returns, but it is not typed as Nothing.
        throw IllegalStateException("Runtime.exit returned unexpectedly")
    }

    private fun logChildStartup(child: Process) {
        val deadline = System.nanoTime() + CHILD_STARTUP_GRACE_MS * 1_000_000
        while (child.isAlive && System.nanoTime() < deadline) {
            Thread.sleep(50)
        }
        if (child.isAlive) {
            Timber.tag(TAG).i(
                "Relaunched process is still alive after %d ms (pid=%d)",
                CHILD_STARTUP_GRACE_MS,
                child.pid(),
            )
        } else {
            Timber.tag(TAG).i(
                "Relaunched process exited during startup (pid=%d, exitCode=%d)",
                child.pid(),
                child.exitValue(),
            )
        }
    }
}
