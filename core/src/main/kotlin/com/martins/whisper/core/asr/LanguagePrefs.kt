package com.martins.whisper.core.asr

/**
 * Onboarding language preferences, independent of any UI.
 *
 * - [primaryLanguage]: the language the user speaks most; never "auto".
 * - [secondaryLanguages]: up to [MAX_SECONDARY] additional languages the user may dictate in.
 * - [stickyAuto]: reuse the first detected language for a while instead of detecting every clip.
 *
 * Auto-detect is constrained to [allowedLanguages]; a detected language outside that set falls
 * back to [primaryLanguage] so the app never silently switches to a language the user did not ask
 * for.
 */
data class LanguagePrefs(
    val primaryLanguage: String = DEFAULT_PRIMARY,
    val secondaryLanguages: List<String> = emptyList(),
    val stickyAuto: Boolean = true,
) {

    /** Valid, de-duplicated, primary-first view; unknown codes are dropped. */
    fun normalized(): LanguagePrefs {
        val primary = WhisperLanguages.byCode(primaryLanguage)?.code ?: DEFAULT_PRIMARY
        val secondaries = secondaryLanguages
            .mapNotNull { WhisperLanguages.byCode(it)?.code }
            .filter { it != primary }
            .distinct()
            .take(MAX_SECONDARY)
        return copy(primaryLanguage = primary, secondaryLanguages = secondaries)
    }

    /** Primary first, then the secondary languages; auto-detect may only choose from these. */
    fun allowedLanguages(): List<String> =
        normalized().let { listOf(it.primaryLanguage) + it.secondaryLanguages }

    fun allows(code: String?): Boolean {
        val valid = WhisperLanguages.byCode(code)?.code ?: return false
        return valid in allowedLanguages()
    }

    /**
     * Language to use for insertion/telemetry after whisper detected [detected]: the detected
     * language when it is in [allowedLanguages], otherwise [primaryLanguage].
     */
    fun resolveDetected(detected: String?): String {
        val valid = WhisperLanguages.byCode(detected)?.code
        return if (valid != null && valid in allowedLanguages()) valid else primaryLanguage
    }

    /** Boring, line-safe encoding for SharedPreferences; see [decode]. */
    fun encode(): String {
        val value = normalized()
        return listOf(
            VERSION,
            value.primaryLanguage,
            value.secondaryLanguages.joinToString(","),
            if (value.stickyAuto) "1" else "0",
        ).joinToString(FIELD_SEPARATOR)
    }

    companion object {

        const val MAX_SECONDARY = 3
        const val DEFAULT_PRIMARY = "en"

        private const val VERSION = "v1"
        private const val FIELD_SEPARATOR = "|"

        /** Languages offered first in pickers; the rest follow alphabetically. */
        val TOP_LANGUAGE_CODES: List<String> = listOf(
            "en", "zh", "es", "hi", "ar", "fr", "pt", "ru", "de", "ja",
            "ko", "it", "tr", "vi", "pl", "nl", "th", "id", "uk", "cs",
        )

        /**
         * Default for a device locale language tag (for example `Locale.getDefault().language`).
         * Legacy/regional tags are mapped to whisper codes, unknown locales fall back to English.
         */
        fun defaultForLocale(localeLanguage: String?): LanguagePrefs {
            val raw = localeLanguage
                ?.trim()
                ?.lowercase()
                ?.substringBefore('-')
                ?.substringBefore('_')
                ?.takeIf { it.isNotEmpty() }
            val mapped = when (raw) {
                "iw" -> "he"
                "in" -> "id"
                "ji" -> "yi"
                "jv" -> "jw"
                "nb" -> "no"
                "fil" -> "tl"
                "cmn" -> "zh"
                else -> raw
            }
            val primary = WhisperLanguages.byCode(mapped)?.code ?: DEFAULT_PRIMARY
            return LanguagePrefs(primaryLanguage = primary)
        }

        /** Top languages first (in [TOP_LANGUAGE_CODES] order), then the rest alphabetically. */
        fun orderTopFirst(languages: List<WhisperLanguage>): List<WhisperLanguage> {
            val rank = TOP_LANGUAGE_CODES.withIndex().associate { it.value to it.index }
            return languages.sortedWith(
                compareBy(
                    { rank[it.code] ?: Int.MAX_VALUE },
                    { it.englishName.lowercase() },
                ),
            )
        }

        /** Decodes [encode] output; null when absent or malformed (caller applies the default). */
        fun decode(raw: String?): LanguagePrefs? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(FIELD_SEPARATOR)
            if (parts.size != 4 || parts[0] != VERSION) return null
            val primary = WhisperLanguages.byCode(parts[1])?.code ?: return null
            val sticky = when (parts[3]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            val secondaries = parts[2]
                .split(',')
                .mapNotNull { WhisperLanguages.byCode(it.trim())?.code }
                .filter { it != primary }
                .distinct()
                .take(MAX_SECONDARY)
            return LanguagePrefs(primary, secondaries, sticky)
        }
    }
}
