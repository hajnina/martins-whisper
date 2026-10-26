package com.martins.whisper.core.asr

/**
 * The languages whisper.cpp accepts, in the engine's own id order. [code] is the ISO 639-1
 * (or whisper-specific) code that must be passed to `whisper_full_params.language`.
 *
 * whisper.cpp ships 100 codes: the 99 original Whisper languages plus Cantonese (`yue`).
 */
data class WhisperLanguage(
    val code: String,
    val englishName: String,
    val nativeName: String,
) {
    fun label(): String = "$englishName ($code)"

    /** Native-script label for a picker, for example "Čeština (cs)". */
    fun nativeLabel(): String = "$nativeName ($code)"
}

object WhisperLanguages {

    const val AUTO = "auto"

    val all: List<WhisperLanguage> = listOf(
        WhisperLanguage("en", "English", "English"),
        WhisperLanguage("zh", "Chinese", "中文"),
        WhisperLanguage("de", "German", "Deutsch"),
        WhisperLanguage("es", "Spanish", "Español"),
        WhisperLanguage("ru", "Russian", "Русский"),
        WhisperLanguage("ko", "Korean", "한국어"),
        WhisperLanguage("fr", "French", "Français"),
        WhisperLanguage("ja", "Japanese", "日本語"),
        WhisperLanguage("pt", "Portuguese", "Português"),
        WhisperLanguage("tr", "Turkish", "Türkçe"),
        WhisperLanguage("pl", "Polish", "Polski"),
        WhisperLanguage("ca", "Catalan", "Català"),
        WhisperLanguage("nl", "Dutch", "Nederlands"),
        WhisperLanguage("ar", "Arabic", "العربية"),
        WhisperLanguage("sv", "Swedish", "Svenska"),
        WhisperLanguage("it", "Italian", "Italiano"),
        WhisperLanguage("id", "Indonesian", "Bahasa Indonesia"),
        WhisperLanguage("hi", "Hindi", "हिन्दी"),
        WhisperLanguage("fi", "Finnish", "Suomi"),
        WhisperLanguage("vi", "Vietnamese", "Tiếng Việt"),
        WhisperLanguage("he", "Hebrew", "עברית"),
        WhisperLanguage("uk", "Ukrainian", "Українська"),
        WhisperLanguage("el", "Greek", "Ελληνικά"),
        WhisperLanguage("ms", "Malay", "Bahasa Melayu"),
        WhisperLanguage("cs", "Czech", "Čeština"),
        WhisperLanguage("ro", "Romanian", "Română"),
        WhisperLanguage("da", "Danish", "Dansk"),
        WhisperLanguage("hu", "Hungarian", "Magyar"),
        WhisperLanguage("ta", "Tamil", "தமிழ்"),
        WhisperLanguage("no", "Norwegian", "Norsk"),
        WhisperLanguage("th", "Thai", "ไทย"),
        WhisperLanguage("ur", "Urdu", "اردو"),
        WhisperLanguage("hr", "Croatian", "Hrvatski"),
        WhisperLanguage("bg", "Bulgarian", "Български"),
        WhisperLanguage("lt", "Lithuanian", "Lietuvių"),
        WhisperLanguage("la", "Latin", "Latina"),
        WhisperLanguage("mi", "Maori", "Māori"),
        WhisperLanguage("ml", "Malayalam", "മലയാളം"),
        WhisperLanguage("cy", "Welsh", "Cymraeg"),
        WhisperLanguage("sk", "Slovak", "Slovenčina"),
        WhisperLanguage("te", "Telugu", "తెలుగు"),
        WhisperLanguage("fa", "Persian", "فارسی"),
        WhisperLanguage("lv", "Latvian", "Latviešu"),
        WhisperLanguage("bn", "Bengali", "বাংলা"),
        WhisperLanguage("sr", "Serbian", "Српски"),
        WhisperLanguage("az", "Azerbaijani", "Azərbaycanca"),
        WhisperLanguage("sl", "Slovenian", "Slovenščina"),
        WhisperLanguage("kn", "Kannada", "ಕನ್ನಡ"),
        WhisperLanguage("et", "Estonian", "Eesti"),
        WhisperLanguage("mk", "Macedonian", "Македонски"),
        WhisperLanguage("br", "Breton", "Brezhoneg"),
        WhisperLanguage("eu", "Basque", "Euskara"),
        WhisperLanguage("is", "Icelandic", "Íslenska"),
        WhisperLanguage("hy", "Armenian", "Հայերեն"),
        WhisperLanguage("ne", "Nepali", "नेपाली"),
        WhisperLanguage("mn", "Mongolian", "Монгол"),
        WhisperLanguage("bs", "Bosnian", "Bosanski"),
        WhisperLanguage("kk", "Kazakh", "Қазақша"),
        WhisperLanguage("sq", "Albanian", "Shqip"),
        WhisperLanguage("sw", "Swahili", "Kiswahili"),
        WhisperLanguage("gl", "Galician", "Galego"),
        WhisperLanguage("mr", "Marathi", "मराठी"),
        WhisperLanguage("pa", "Punjabi", "ਪੰਜਾਬੀ"),
        WhisperLanguage("si", "Sinhala", "සිංහල"),
        WhisperLanguage("km", "Khmer", "ខ្មែរ"),
        WhisperLanguage("sn", "Shona", "ChiShona"),
        WhisperLanguage("yo", "Yoruba", "Yorùbá"),
        WhisperLanguage("so", "Somali", "Soomaali"),
        WhisperLanguage("af", "Afrikaans", "Afrikaans"),
        WhisperLanguage("oc", "Occitan", "Occitan"),
        WhisperLanguage("ka", "Georgian", "ქართული"),
        WhisperLanguage("be", "Belarusian", "Беларуская"),
        WhisperLanguage("tg", "Tajik", "Тоҷикӣ"),
        WhisperLanguage("sd", "Sindhi", "سنڌي"),
        WhisperLanguage("gu", "Gujarati", "ગુજરાતી"),
        WhisperLanguage("am", "Amharic", "አማርኛ"),
        WhisperLanguage("yi", "Yiddish", "ייִדיש"),
        WhisperLanguage("lo", "Lao", "ລາວ"),
        WhisperLanguage("uz", "Uzbek", "Oʻzbekcha"),
        WhisperLanguage("fo", "Faroese", "Føroyskt"),
        WhisperLanguage("ht", "Haitian Creole", "Kreyòl ayisyen"),
        WhisperLanguage("ps", "Pashto", "پښتو"),
        WhisperLanguage("tk", "Turkmen", "Türkmençe"),
        WhisperLanguage("nn", "Nynorsk", "Nynorsk"),
        WhisperLanguage("mt", "Maltese", "Malti"),
        WhisperLanguage("sa", "Sanskrit", "संस्कृतम्"),
        WhisperLanguage("lb", "Luxembourgish", "Lëtzebuergesch"),
        WhisperLanguage("my", "Myanmar", "မြန်မာ"),
        WhisperLanguage("bo", "Tibetan", "བོད་སྐད་"),
        WhisperLanguage("tl", "Tagalog", "Tagalog"),
        WhisperLanguage("mg", "Malagasy", "Malagasy"),
        WhisperLanguage("as", "Assamese", "অসমীয়া"),
        WhisperLanguage("tt", "Tatar", "Татарча"),
        WhisperLanguage("haw", "Hawaiian", "ʻŌlelo Hawaiʻi"),
        WhisperLanguage("ln", "Lingala", "Lingála"),
        WhisperLanguage("ha", "Hausa", "Hausa"),
        WhisperLanguage("ba", "Bashkir", "Башҡортса"),
        WhisperLanguage("jw", "Javanese", "Basa Jawa"),
        WhisperLanguage("su", "Sundanese", "Basa Sunda"),
        WhisperLanguage("yue", "Cantonese", "粵語"),
    )

    private val byCode: Map<String, WhisperLanguage> = all.associateBy { it.code }

    fun byCode(code: String?): WhisperLanguage? = code?.let { byCode[it] }

    fun isAuto(code: String?): Boolean = code == null || code == AUTO

    /** True for [AUTO] and for every code whisper.cpp knows. */
    fun isSupported(code: String?): Boolean = isAuto(code) || byCode(code) != null

    /** Human label for a code; unknown codes are returned unchanged. */
    fun label(code: String?): String = when {
        isAuto(code) -> "Auto"
        else -> byCode(code)?.label() ?: code.orEmpty()
    }

    /** Native-script label for a code; unknown codes are returned unchanged. */
    fun nativeLabel(code: String?): String = when {
        isAuto(code) -> "Auto"
        else -> byCode(code)?.nativeLabel() ?: code.orEmpty()
    }
}
