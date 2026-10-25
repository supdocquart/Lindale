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

    fun restart(): Nothing {
        try {
            val launcher = System.getProperty(JPACKAGE_APP_PATH)
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)

            if (launcher != null && launcher.isFile) {
                ProcessBuilder(launcher.absolutePath).start()
            } else {
                // DESKTOP: dev builds are started by Gradle, so replay the running java command.
                val info = ProcessHandle.current().info()
                val command = info.command().orElse(null)
                val arguments = info.arguments().orElse(null)
                if (command != null && arguments != null) {
                    ProcessBuilder(listOf(command) + arguments).start()
                } else {
                    Timber.tag(TAG).w(
                        "Cannot relaunch the app: command=%s, arguments=%s",
                        command,
                        arguments?.size,
                    )
                }
            }
        } catch (e: Throwable) {
            // A relaunch failure must never prevent the process from exiting.
            Timber.tag(TAG).e(e, "Failed to relaunch the app")
        }

        Runtime.getRuntime().exit(0)
        // Runtime.exit never returns, but it is not typed as Nothing.
        throw IllegalStateException("Runtime.exit returned unexpectedly")
    }
}
