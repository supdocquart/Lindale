package com.metrolist.music.recognition

import com.metrolist.shazamkit.models.RecognitionResult
import com.metrolist.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MusicRecognitionService {
    private val _recognitionStatus = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Ready)
    val recognitionStatus: StateFlow<RecognitionStatus> = _recognitionStatus.asStateFlow()

    var resultSavedExternally: Boolean = false

    fun reset() {
        _recognitionStatus.value = RecognitionStatus.Ready
        resultSavedExternally = false
    }

    suspend fun recognize(context: Any?) {
        // Recognition logic
    }

    fun hasRecordPermission(context: Any?): Boolean = true
}
