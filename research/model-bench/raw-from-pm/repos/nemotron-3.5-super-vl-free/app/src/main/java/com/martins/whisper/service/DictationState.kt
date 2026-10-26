package com.martins.whisper.service

sealed interface DictationState {
    data object Idle : DictationState
    data class Loading(val label: String = "Loading model\u2026") : DictationState
    data class Recording(
        val elapsedMs: Long,
        val level: Float,
        val speechDetected: Boolean,
    ) : DictationState
    data class Processing(val label: String = "Transcribing\u2026") : DictationState
    data class Success(val message: String) : DictationState
    data class Error(val message: String) : DictationState

    val isBusy: Boolean
        get() = this !is Idle && this !is Success && this !is Error

    val overlayExpanded: Boolean
        get() = this !is Idle
}
