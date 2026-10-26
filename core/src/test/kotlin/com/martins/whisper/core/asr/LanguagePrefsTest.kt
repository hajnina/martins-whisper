package com.martins.whisper.core.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePrefsTest {

    @Test
    fun `device locale picks the matching whisper language`() {
        assertEquals("cs", LanguagePrefs.defaultForLocale("cs").primaryLanguage)
        assertEquals("de", LanguagePrefs.defaultForLocale("de-AT").primaryLanguage)
        assertEquals("pt", LanguagePrefs.defaultForLocale("pt_BR").primaryLanguage)
        assertEquals("he", LanguagePrefs.defaultForLocale("iw").primaryLanguage)
        assertEquals("id", LanguagePrefs.defaultForLocale("in").primaryLanguage)
        assertEquals("no", LanguagePrefs.defaultForLocale("nb").primaryLanguage)
        assertEquals("jw", LanguagePrefs.defaultForLocale("jv").primaryLanguage)
        assertEquals("en", LanguagePrefs.defaultForLocale("xx").primaryLanguage)
        assertEquals("en", LanguagePrefs.defaultForLocale(null).primaryLanguage)
        assertEquals("en", LanguagePrefs.defaultForLocale("  ").primaryLanguage)
    }

    @Test
    fun `defaults are primary from locale, no secondaries and sticky auto on`() {
        val prefs = LanguagePrefs.defaultForLocale("cs")
        assertEquals("cs", prefs.primaryLanguage)
        assertTrue(prefs.secondaryLanguages.isEmpty())
        assertTrue(prefs.stickyAuto)
    }

    @Test
    fun `normalization drops unknown and duplicate secondaries and caps at three`() {
        val prefs = LanguagePrefs(
            primaryLanguage = "cs",
            secondaryLanguages = listOf("de", "de", "sk", "xx", "cs", "en", "pl", "fr"),
        ).normalized()

        assertEquals("cs", prefs.primaryLanguage)
        assertEquals(listOf("de", "sk", "en"), prefs.secondaryLanguages)
    }

    @Test
    fun `an invalid primary falls back to english`() {
        assertEquals("en", LanguagePrefs(primaryLanguage = "xx").normalized().primaryLanguage)
        assertEquals("en", LanguagePrefs(primaryLanguage = "auto").normalized().primaryLanguage)
    }

    @Test
    fun `allowed languages are primary first then secondaries`() {
        val prefs = LanguagePrefs("cs", listOf("de", "sk"), stickyAuto = true)
        assertEquals(listOf("cs", "de", "sk"), prefs.allowedLanguages())
        assertTrue(prefs.allows("cs"))
        assertTrue(prefs.allows("sk"))
        assertFalse(prefs.allows("en"))
        assertFalse(prefs.allows(null))
        assertFalse(prefs.allows("auto"))
    }

    @Test
    fun `detected language outside the allowed set falls back to primary`() {
        val prefs = LanguagePrefs("cs", listOf("de", "sk"))

        assertEquals("cs", prefs.resolveDetected("cs"))
        assertEquals("de", prefs.resolveDetected("de"))
        assertEquals("sk", prefs.resolveDetected("sk"))
        assertEquals("cs", prefs.resolveDetected("en"))
        assertEquals("cs", prefs.resolveDetected("ja"))
        assertEquals("cs", prefs.resolveDetected(null))
        assertEquals("cs", prefs.resolveDetected("xx"))
    }

    @Test
    fun `encode decode round trips all fields`() {
        val prefs = LanguagePrefs("cs", listOf("de", "sk"), stickyAuto = false)
        val decoded = LanguagePrefs.decode(prefs.encode())

        assertEquals(prefs, decoded)
        assertEquals("v1|cs|de,sk|0", prefs.encode())
    }

    @Test
    fun `decode rejects malformed values and filters bad secondaries`() {
        assertNull(LanguagePrefs.decode(null))
        assertNull(LanguagePrefs.decode(""))
        assertNull(LanguagePrefs.decode("nope"))
        assertNull(LanguagePrefs.decode("v1|xx|de|1"))
        assertNull(LanguagePrefs.decode("v1|cs|de|maybe"))
        assertNull(LanguagePrefs.decode("v2|cs|de|1"))

        assertEquals(
            LanguagePrefs("cs", listOf("de", "sk")),
            LanguagePrefs.decode("v1|cs|de,xx,sk,de|1"),
        )
    }

    @Test
    fun `top languages come first and the rest stay alphabetical`() {
        val ordered = LanguagePrefs.orderTopFirst(
            listOf(
                WhisperLanguages.byCode("yue")!!,
                WhisperLanguages.byCode("cs")!!,
                WhisperLanguages.byCode("zh")!!,
                WhisperLanguages.byCode("en")!!,
                WhisperLanguages.byCode("haw")!!,
            ),
        ).map { it.code }

        assertEquals(listOf("en", "zh", "cs", "yue", "haw"), ordered)
    }

    @Test
    fun `all whisper languages are orderable and have native names`() {
        val ordered = LanguagePrefs.orderTopFirst(WhisperLanguages.all)
        assertEquals(WhisperLanguages.all.size, ordered.size)
        assertEquals("en", ordered.first().code)
        for (language in WhisperLanguages.all) {
            assertTrue(language.nativeName.isNotBlank())
        }
        assertEquals("Čeština", WhisperLanguages.byCode("cs")?.nativeName)
        assertEquals("粵語", WhisperLanguages.byCode("yue")?.nativeName)
    }
}
