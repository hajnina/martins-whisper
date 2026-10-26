package com.martins.whisper.core

/**
 * Decides whether a whisper transcript may be inserted. Silence makes whisper hallucinate
 * "Thank you.", "Thanks for watching!" or bracketed markers such as [BLANK_AUDIO]; those must
 * never reach the target field.
 */
object TranscriptGate {

    const val EMPTY_MESSAGE: String = "Didn't catch that"
    const val SILENT_MIC_MESSAGE: String = "Microphone returned silence"

    sealed interface Decision {
        data class Speak(val text: String) : Decision
        data class Reject(val reason: String, val message: String = EMPTY_MESSAGE) : Decision
    }

    private val NON_SPEECH_TOKENS = Regex("\\[[^\\]]*\\]|\\([^)]*\\)|<[^>]*>")

    private val HALLUCINATIONS: Set<String> = setOf(
        "thank you",
        "thank you very much",
        "thanks",
        "thanks for watching",
        "thank you for watching",
        "please subscribe",
        "like and subscribe",
        "subscribe",
        "bye",
        "bye bye",
        "you",
    )

    fun evaluate(raw: String, speechDetected: Boolean, peakLevel: Float): Decision {
        if (peakLevel <= 0f) return Decision.Reject("silent microphone", SILENT_MIC_MESSAGE)
        if (!speechDetected) return Decision.Reject("no speech energy")
        val cleaned = clean(raw)
        if (cleaned.isEmpty()) return Decision.Reject("empty transcript")
        if (isHallucination(cleaned)) return Decision.Reject("hallucinated non-speech")
        return Decision.Speak(cleaned)
    }

    fun clean(raw: String): String =
        raw.replace(NON_SPEECH_TOKENS, " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun isHallucination(text: String): Boolean {
        val normalized = text.lowercase()
            .trim()
            .trim('.', '!', '?', ',', ';', ':', '"', '\'')
            .trim()
        return normalized in HALLUCINATIONS
    }
}
