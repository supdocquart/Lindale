package com.metrolist.music.utils
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import com.metrolist.music.PlatformContext

class CrashHandler private constructor(
    private val applicationContext: PlatformContext
) : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    override fun uncaughtException(t: Thread, e: Throwable) {}
    companion object {
        fun install(context: PlatformContext) {}
    }
}
