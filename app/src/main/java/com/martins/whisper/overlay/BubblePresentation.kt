package com.martins.whisper.overlay

import com.martins.whisper.service.DictationState
import com.martins.whisper.ui.theme.WhisperOverlayPalette

enum class BubbleIcon {
    MIC,
    MIC_LEVELS,
    SPINNER,
    TRANSCRIBING,
    CHECK,
    CROSS,
}

data class BubbleVisual(
    val accent: Int,
    val icon: BubbleIcon,
    val contentDescription: String,
    val label: String?,
)

object BubblePresentation {

    val IDLE_ACCENT: Int = WhisperOverlayPalette.IdleAccent
    val LOADING_ACCENT: Int = WhisperOverlayPalette.LoadingAccent
    val RECORDING_ACCENT: Int = WhisperOverlayPalette.RecordingAccent
    val PROCESSING_ACCENT: Int = WhisperOverlayPalette.ProcessingAccent
    val SUCCESS_ACCENT: Int = WhisperOverlayPalette.SuccessAccent
    val ERROR_ACCENT: Int = WhisperOverlayPalette.ErrorAccent

    fun of(state: DictationState): BubbleVisual = when (state) {
        is DictationState.Idle -> BubbleVisual(
            accent = IDLE_ACCENT,
            icon = BubbleIcon.MIC,
            contentDescription = "Tap to dictate",
            label = null,
        )

        is DictationState.Loading -> BubbleVisual(
            accent = LOADING_ACCENT,
            icon = BubbleIcon.SPINNER,
            contentDescription = state.label.ifBlank { "Loading model\u2026" },
            label = state.label,
        )

        is DictationState.Recording -> BubbleVisual(
            accent = LISTENING_ACCENT,
            icon = BubbleIcon.MIC_LEVELS,
            contentDescription = "Listening; tap to stop",
            label = null,
        )

        is DictationState.Processing -> BubbleVisual(
            accent = TRANSCRIBING_ACCENT,
            icon = BubbleIcon.TRANSCRIBING,
            contentDescription = "Transcribing\u2026",
            label = state.label,
        )

        is DictationState.Success -> BubbleVisual(
            accent = SUCCESS_ACCENT,
            icon = BubbleIcon.CHECK,
            contentDescription = state.message,
            label = state.message,
        )

        is DictationState.Error -> BubbleVisual(
            accent = ERROR_ACCENT,
            icon = BubbleIcon.CROSS,
            contentDescription = "Error: ${state.message}",
            label = state.message,
        )
    }
}
