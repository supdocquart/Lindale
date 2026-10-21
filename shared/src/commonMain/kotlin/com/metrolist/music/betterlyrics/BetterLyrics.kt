package com.metrolist.music.betterlyrics

import com.metrolist.music.betterlyrics.models.TTMLResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Word-synced (TTML) lyrics from the BetterLyrics API, converted to the app's LRC format. */
object BetterLyrics {
    private const val API_BASE_URL = "https://lyrics-api.boidu.dev/"

    private val client by lazy {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
            install(ContentNegotiation) {
                json(Json { isLenient = true; ignoreUnknownKeys = true })
            }
            defaultRequest { url(API_BASE_URL) }
            expectSuccess = true
        }
    }

    private suspend fun fetchTTML(title: String, artist: String, duration: Long): String? =
        client.get("getLyrics") {
            parameter("s", title)
            parameter("a", artist)
            if (duration > 0) parameter("d", duration)
        }.body<TTMLResponse>().ttml?.takeIf { it.isNotBlank() }

    suspend fun getLyrics(title: String, artist: String, duration: Long): String? {
        val ttml = runCatching { fetchTTML(title, artist, duration) }
            .onFailure { timber.log.Timber.d(it, "BetterLyrics: request failed") }
            .getOrNull() ?: return null
        val lines = TTMLParser.parseTTML(ttml)
        if (lines.isEmpty()) return null
        return TTMLParser.toLRC(lines).takeIf { it.isNotBlank() }
    }
}
