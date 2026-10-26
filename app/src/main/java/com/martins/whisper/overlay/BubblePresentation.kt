package com.martins.whisper.overlay

import com.martins.whisper.service.DictationState

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

    val IDLE_ACCENT: Int = 0xFF135D66.toInt()
    val LOADING_ACCENT: Int = 0xFFB26A00.toInt()
    val LISTENING_ACCENT: Int = 0xFFC62828.toInt()
    val TRANSCRIBING_ACCENT: Int = 0xFF1565C0.toInt()
    val SUCCESS_ACCENT: Int = 0xFF2E7D32.toInt()
    val ERROR_ACCENT: Int = 0xFFB00020.toInt()

    fun of(state: DictationState): BubbleVisual = when (state) {
        is DictationState.Idle -> BubbleVisual(
            accent = IDLE_ACCENT,
            icon = BubbleIcon.MIC,
            contentDescription = "Hold to dictate",
            label = null,
        )

        is DictationState.Loading -> BubbleVisual(
            accent = LOADING_ACCENT,
            icon = BubbleIcon.SPINNER,
            contentDescription = joinDistinct("Loading model\u2026", state.label),
            label = state.label,
        )

        is DictationState.Recording -> BubbleVisual(
            accent = LISTENING_ACCENT,
            icon = BubbleIcon.MIC_LEVELS,
            contentDescription = "Listening; release to transcribe",
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

    private fun joinDistinct(vararg parts: String): String =
        parts.filter { it.isNotBlank() }.distinct().joinToString(" ")
}
