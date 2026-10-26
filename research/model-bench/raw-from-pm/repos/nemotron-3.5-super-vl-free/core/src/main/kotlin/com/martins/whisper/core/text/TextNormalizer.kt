package com.martins.whisper.core.text

object TextNormalizer {
    private val hallucinations = setOf(
        "thank you.",
        "thank you",
        "thank you for watching.",
        "thank you for watching",
        "thanks for watching.",
        "thanks for watching!",
        "thanks for watching",
        "please subscribe.",
        "please subscribe",
        "[blank_audio]",
        "[ blank_audio ]",
        "[blank audio]",
        "[ blank audio ]",
        "[silence]",
        "[ silence ]",
        "(silence)",
        "*silence*",
        "[music]",
        "[ music ]",
        "[applause]",
        "[laughter]",
        "(upbeat music)",
        "...",
        "you",
    )

    private val whitespace = Regex("\\s+")
    private val spaceBeforePunctuation = Regex("\\s+([,.;:!?])")

    fun normalize(text: String): String {
        var value = text.replace(whitespace, " ").trim()
        value = spaceBeforePunctuation.replace(value, "$1")
        if (value.isBlank()) return ""
        if (hallucinations.contains(value.lowercase())) return ""
        return value
    }
}
