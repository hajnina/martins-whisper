package com.martins.whisper.core.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperLanguagesTest {

    @Test
    fun `contains the full whisper language list`() {
        // whisper.cpp ships 100 codes: the 99 original Whisper languages plus Cantonese (yue).
        assertEquals(100, WhisperLanguages.all.size)
        assertEquals(100, WhisperLanguages.all.map { it.code }.toSet().size)
        assertEquals(100, WhisperLanguages.all.map { it.englishName }.toSet().size)
        assertEquals(100, WhisperLanguages.all.map { it.nativeName }.toSet().size)
    }

    @Test
    fun `includes the languages the acceptance list needs`() {
        val required = listOf(
            "en", "cs", "de", "es", "fr", "ru", "uk", "pl", "el", "ar", "he", "hi",
            "th", "ja", "zh", "ko", "tr", "pt", "it", "vi",
        )
        for (code in required) {
            assertTrue("missing $code", WhisperLanguages.byCode(code) != null)
        }
    }

    @Test
    fun `whisper specific codes are present`() {
        assertEquals("Hawaiian", WhisperLanguages.byCode("haw")?.englishName)
        assertEquals("Cantonese", WhisperLanguages.byCode("yue")?.englishName)
        assertEquals("Javanese", WhisperLanguages.byCode("jw")?.englishName)
    }

    @Test
    fun `auto is not part of the language list`() {
        assertNull(WhisperLanguages.byCode("auto"))
        assertTrue(WhisperLanguages.isAuto(null))
        assertTrue(WhisperLanguages.isAuto("auto"))
        assertFalse(WhisperLanguages.isAuto("en"))
        assertTrue(WhisperLanguages.isSupported("auto"))
        assertTrue(WhisperLanguages.isSupported("cs"))
        assertFalse(WhisperLanguages.isSupported("xx"))
        assertFalse(WhisperLanguages.isSupported(""))
    }

    @Test
    fun `labels show the english or native name and code`() {
        assertEquals("Czech (cs)", WhisperLanguages.label("cs"))
        assertEquals("Cantonese (yue)", WhisperLanguages.label("yue"))
        assertEquals("Auto", WhisperLanguages.label(null))
        assertEquals("xx", WhisperLanguages.label("xx"))
        assertEquals("Čeština (cs)", WhisperLanguages.nativeLabel("cs"))
        assertEquals("中文 (zh)", WhisperLanguages.nativeLabel("zh"))
        assertEquals("Auto", WhisperLanguages.nativeLabel("auto"))
    }
}
