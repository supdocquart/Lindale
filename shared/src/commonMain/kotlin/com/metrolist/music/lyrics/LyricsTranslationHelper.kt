package com.metrolist.music.lyrics

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object LyricsTranslationHelper {
    sealed class TranslationStatus {
        object Idle : TranslationStatus()
        object Translating : TranslationStatus()
        data class Success(val lyrics: Any? = null) : TranslationStatus()
        data class Error(val message: String? = null) : TranslationStatus()
    }

    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    private val _manualTrigger = MutableSharedFlow<Unit>()
    val manualTrigger: SharedFlow<Unit> = _manualTrigger.asSharedFlow()

    private val _clearTranslationsTrigger = MutableSharedFlow<Unit>()
    val clearTranslationsTrigger: SharedFlow<Unit> = _clearTranslationsTrigger.asSharedFlow()

    val hasActiveTranslations: StateFlow<Boolean> = MutableStateFlow(false)

    fun <T> clearTranslations(lyrics: T): T = lyrics
    fun triggerClearTranslations() {}
    fun triggerManualTranslation() {}

    fun setCompositionActive(active: Boolean) {}
    fun cancelTranslation() {
        _status.value = TranslationStatus.Idle
    }
    fun loadTranslationsFromDatabase(
        lyrics: Any? = null,
        lyricsEntity: Any? = null,
        targetLanguage: Any? = null,
        mode: Any? = null,
    ) {}
    fun translateLyrics(
        lyrics: Any? = null,
        targetLanguage: Any? = null,
        apiKey: String? = null,
        baseUrl: String? = null,
        model: String? = null,
        mode: Any? = null,
        scope: Any? = null,
        context: Any? = null,
        provider: Any? = null,
        deeplApiKey: String? = null,
        deeplFormality: Any? = null,
        useStreaming: Boolean = false,
        songId: String = "",
        database: Any? = null,
        systemPrompt: String? = null,
    ) {}
}
