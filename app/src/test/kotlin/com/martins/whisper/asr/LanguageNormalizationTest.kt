package com.martins.whisper.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageNormalizationTest {

    @Test
    fun `blank and auto mean auto detect`() {
        assertNull(normalizeLanguage(null))
        assertNull(normalizeLanguage(""))
        assertNull(normalizeLanguage("   "))
        assertNull(normalizeLanguage("auto"))
        assertNull(normalizeLanguage(" AUTO "))
    }

    @Test
    fun `explicit languages are trimmed and lowercased`() {
        assertEquals("en", normalizeLanguage(" EN "))
        assertEquals("cs", normalizeLanguage(" cs "))
    }
}
