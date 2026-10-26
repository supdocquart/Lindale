package com.metrolist.music.platform

/**
 * DESKTOP: hook for the embedded "Sign in with Google" window. Upstream logs in through an Android
 * WebView; on desktop the window is implemented with JetBrains Runtime's JCEF in the desktop module
 * (JcefGoogleLogin.kt), which sets [opener] at startup. Without JCEF the login screen keeps only the
 * cookie-paste method.
 */
object GoogleLogin {
    sealed interface Result {
        data class Success(
            val cookie: String,
            val visitorData: String,
            val dataSyncId: String,
            val authUser: String,
        ) : Result

        /** Google refused to sign in inside the embedded browser ("This browser may not be secure"). */
        data object Blocked : Result

        data object Cancelled : Result

        data class Failed(val reason: String) : Result
    }

    @Volatile
    var opener: ((onResult: (Result) -> Unit) -> Unit)? = null

    val isAvailable: Boolean get() = opener != null

    /** Localised title for the sign-in window (Res is internal to this module). */
    suspend fun windowTitle(): String =
        org.jetbrains.compose.resources.getString(metrolist.shared.generated.resources.Res.string.sign_in_with_google)

    fun open(onResult: (Result) -> Unit) {
        val open = opener
        if (open == null) onResult(Result.Failed("embedded browser unavailable")) else open(onResult)
    }
}
