package com.metrolist.music.discord

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

data class AuthCodeResult(val code: String, val state: String)

object DiscordOAuthActivity {
    @Volatile
    private var deferred: CompletableDeferred<AuthCodeResult>? = null

    fun newDeferred(): CompletableDeferred<AuthCodeResult> {
        val d = CompletableDeferred<AuthCodeResult>()
        deferred = d
        return d
    }

    suspend fun awaitCode(timeoutMs: Long = 120_000L): AuthCodeResult {
        val d = deferred ?: throw CancellationException("No pending authorization")
        return withTimeout(timeoutMs) { d.await() }
    }

    /** Platform hook that starts listening for the OAuth redirect; the returned handle stops it. */
    @Volatile
    var platformReceiver: (() -> AutoCloseable?)? = null

    fun complete(code: String, state: String): Boolean =
        deferred?.complete(AuthCodeResult(code, state)) ?: false

    fun fail(message: String) {
        deferred?.let { d ->
            if (!d.isCompleted) d.completeExceptionally(CancellationException(message))
        }
    }

    fun cancelPending() {
        deferred?.let { d ->
            if (!d.isCompleted) {
                d.completeExceptionally(CancellationException("Authorization cancelled by user"))
            }
        }
    }
}
