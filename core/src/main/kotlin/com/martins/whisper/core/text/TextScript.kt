package com.martins.whisper.core.text

/**
 * Script properties that matter when fitting dictated text into a field:
 * - [cased]: the script uses upper/lower case, so sentence casing is meaningful.
 * - [wordSpaces]: words are separated by spaces, so a leading space may be needed. CJK and Thai
 *   (plus Lao, Khmer, Myanmar) run words together and must never get an inserted space.
 */
enum class TextScript(val cased: Boolean, val wordSpaces: Boolean) {
    LATIN(true, true),
    GREEK(true, true),
    CYRILLIC(true, true),
    ARMENIAN(true, true),
    HEBREW(false, true),
    ARABIC(false, true),
    DEVANAGARI(false, true),
    BENGALI(false, true),
    GURMUKHI(false, true),
    GUJARATI(false, true),
    TAMIL(false, true),
    TELUGU(false, true),
    KANNADA(false, true),
    MALAYALAM(false, true),
    SINHALA(false, true),
    THAI(false, false),
    LAO(false, false),
    KHMER(false, false),
    MYANMAR(false, false),
    HAN(false, false),
    HIRAGANA(false, false),
    KATAKANA(false, false),
    HANGUL(false, true),
    GEORGIAN(false, true),
    TIBETAN(false, true),
    ETHIOPIC(false, true),
    OTHER(false, true),
    ;

    companion object {

        private val CASELESS_LANGUAGES = setOf(
            "zh", "yue", "ja", "ko", "th", "lo", "km", "my", "bo",
            "ar", "he", "fa", "ur", "sd", "ps", "yi",
            "hi", "bn", "pa", "gu", "ta", "te", "kn", "ml", "si", "ne", "mr", "sa", "as",
            "am", "ka",
        )

        private val NO_SPACE_LANGUAGES = setOf("zh", "yue", "ja", "th", "lo", "km", "my")

        /** Script implied by a whisper language code, or null when the code is unknown/auto. */
        fun forLanguage(code: String?): TextScript? {
            val language = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "auto" }
                ?: return null
            return values().firstOrNull { it.matchesLanguage(language) }
                ?: TextScript.OTHER.withLanguage(language)
        }

        /** Script of the first strong character in [text]; [OTHER] when it has none. */
        fun forText(text: String): TextScript {
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                if (Character.isLetter(codePoint) || Character.isIdeographic(codePoint)) {
                    return forCodePoint(codePoint)
                }
                index += Character.charCount(codePoint)
            }
            return OTHER
        }

        private fun TextScript.matchesLanguage(language: String): Boolean =
            when (this) {
                LATIN -> language in LATIN_LANGUAGES
                GREEK -> language == "el"
                CYRILLIC -> language in CYRILLIC_LANGUAGES
                ARMENIAN -> language == "hy"
                HEBREW -> language == "he" || language == "yi"
                ARABIC -> language in ARABIC_LANGUAGES
                DEVANAGARI -> language in setOf("hi", "mr", "ne", "sa", "as")
                BENGALI -> language == "bn"
                GURMUKHI -> language == "pa"
                GUJARATI -> language == "gu"
                TAMIL -> language == "ta"
                TELUGU -> language == "te"
                KANNADA -> language == "kn"
                MALAYALAM -> language == "ml"
                SINHALA -> language == "si"
                THAI -> language == "th"
                LAO -> language == "lo"
                KHMER -> language == "km"
                MYANMAR -> language == "my"
                HAN -> language in setOf("zh", "yue", "ja")
                HIRAGANA, KATAKANA -> language == "ja"
                HANGUL -> language == "ko"
                GEORGIAN -> language == "ka"
                TIBETAN -> language == "bo"
                ETHIOPIC -> language == "am"
                OTHER -> false
            }

        private fun TextScript.withLanguage(language: String): TextScript = when {
            language in NO_SPACE_LANGUAGES -> when (language) {
                "th" -> THAI
                "lo" -> LAO
                "km" -> KHMER
                "my" -> MYANMAR
                "ja" -> HIRAGANA
                else -> HAN
            }
            language in CASELESS_LANGUAGES -> OTHER
            else -> LATIN
        }

        private fun forCodePoint(codePoint: Int): TextScript =
            when (Character.UnicodeScript.of(codePoint)) {
                Character.UnicodeScript.LATIN -> TextScript.LATIN
                Character.UnicodeScript.GREEK -> TextScript.GREEK
                Character.UnicodeScript.CYRILLIC -> TextScript.CYRILLIC
                Character.UnicodeScript.ARMENIAN -> TextScript.ARMENIAN
                Character.UnicodeScript.HEBREW -> TextScript.HEBREW
                Character.UnicodeScript.ARABIC -> TextScript.ARABIC
                Character.UnicodeScript.DEVANAGARI -> TextScript.DEVANAGARI
                Character.UnicodeScript.BENGALI -> TextScript.BENGALI
                Character.UnicodeScript.GURMUKHI -> TextScript.GURMUKHI
                Character.UnicodeScript.GUJARATI -> TextScript.GUJARATI
                Character.UnicodeScript.TAMIL -> TextScript.TAMIL
                Character.UnicodeScript.TELUGU -> TextScript.TELUGU
                Character.UnicodeScript.KANNADA -> TextScript.KANNADA
                Character.UnicodeScript.MALAYALAM -> TextScript.MALAYALAM
                Character.UnicodeScript.SINHALA -> TextScript.SINHALA
                Character.UnicodeScript.THAI -> TextScript.THAI
                Character.UnicodeScript.LAO -> TextScript.LAO
                Character.UnicodeScript.KHMER -> TextScript.KHMER
                Character.UnicodeScript.MYANMAR -> TextScript.MYANMAR
                Character.UnicodeScript.HAN -> TextScript.HAN
                Character.UnicodeScript.HIRAGANA -> TextScript.HIRAGANA
                Character.UnicodeScript.KATAKANA -> TextScript.KATAKANA
                Character.UnicodeScript.HANGUL -> TextScript.HANGUL
                Character.UnicodeScript.GEORGIAN -> TextScript.GEORGIAN
                Character.UnicodeScript.TIBETAN -> TextScript.TIBETAN
                Character.UnicodeScript.ETHIOPIC -> TextScript.ETHIOPIC
                else -> TextScript.OTHER
            }

        private val LATIN_LANGUAGES = setOf(
            "en", "de", "cs", "es", "fr", "pt", "it", "nl", "sv", "da", "no", "nn", "fi",
            "pl", "sk", "sl", "hr", "bs", "ro", "hu", "et", "lv", "lt", "tr", "az", "uz",
            "tk", "ca", "gl", "oc", "br", "eu", "is", "fo", "lb", "mt", "cy", "haw", "mi",
            "tl", "mg", "ln", "ha", "yo", "sn", "so", "af", "sw", "ht", "id", "ms", "vi",
            "jw", "su", "la",
        )

        private val CYRILLIC_LANGUAGES = setOf(
            "ru", "uk", "bg", "sr", "mk", "be", "kk", "mn", "tg", "tt", "ba",
        )

        private val ARABIC_LANGUAGES = setOf("ar", "fa", "ur", "sd", "ps", "tk")
    }
}
