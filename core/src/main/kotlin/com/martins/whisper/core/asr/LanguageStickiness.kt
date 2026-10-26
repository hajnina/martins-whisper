package com.martins.whisper.core.asr

/**
 * Sticky auto-detect: when the user picked [WhisperLanguages.AUTO], the first dictation lets
 * whisper.cpp detect the language and the following dictations reuse that detection for [ttlMs].
 * Short (2-5 s) clips make auto-detect flip between languages, so detecting once per session and
 * sticking to it is both cheaper and more stable. An explicit language always wins.
 */
class LanguageStickiness(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long,
) {

    private var detected: String? = null
    private var detectedAtMs: Long = 0L

    /** Language to pass to the engine: null means "let whisper detect". */
    @Synchronized
    fun requestLanguage(selected: String?): String? {
        if (!WhisperLanguages.isAuto(selected)) return selected
        val current = detected ?: return null
        if (clock() - detectedAtMs > ttlMs) {
            detected = null
            return null
        }
        return current
    }

    /**
     * Records the language the engine detected for the last request. Ignored for explicit
     * selections and for blank/auto results.
     */
    @Synchronized
    fun onDetected(detectedLanguage: String?, selected: String?) {
        if (!WhisperLanguages.isAuto(selected)) return
        val language = detectedLanguage?.trim()?.takeIf {
            it.isNotEmpty() && it != WhisperLanguages.AUTO && WhisperLanguages.byCode(it) != null
        } ?: return
        detected = language
        detectedAtMs = clock()
    }

    @Synchronized
    fun reset() {
        detected = null
        detectedAtMs = 0L
    }

    @Synchronized
    fun detectedLanguage(): String? = detected

    companion object {
        /** How long one detection is reused; a new detection happens after this window. */
        const val DEFAULT_TTL_MS: Long = 10 * 60 * 1000L
    }
}
