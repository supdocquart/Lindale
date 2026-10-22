package com.metrolist.music.utils
import com.metrolist.music.platform.AppContext
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource


class CrashHandler private constructor(
    private val applicationContext: AppContext
) : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    override fun uncaughtException(t: Thread, e: Throwable) {}
    companion object {
        fun install(context: AppContext) {}
    }
}
