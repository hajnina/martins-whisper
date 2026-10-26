package com.martins.whisper.core.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageStickinessTest {

    private var now = 1_000_000L
    private val sticky = LanguageStickiness(ttlMs = 10 * 60 * 1000L, clock = { now })

    @Test
    fun `auto requests detection until the engine reports a language`() {
        assertNull(sticky.requestLanguage("auto"))
        assertNull(sticky.requestLanguage(null))
        sticky.onDetected(null, "auto")
        assertNull(sticky.requestLanguage("auto"))
        sticky.onDetected("", "auto")
        assertNull(sticky.requestLanguage("auto"))
    }

    @Test
    fun `detected language is reused within the ttl`() {
        assertNull(sticky.requestLanguage("auto"))
        sticky.onDetected("cs", "auto")

        assertEquals("cs", sticky.requestLanguage("auto"))
        assertEquals("cs", sticky.requestLanguage(null))
        assertEquals("cs", sticky.detectedLanguage())

        now += 10 * 60 * 1000L
        assertEquals("cs", sticky.requestLanguage("auto"))

        now += 1
        assertNull(sticky.requestLanguage("auto"))
        assertNull(sticky.detectedLanguage())
    }

    @Test
    fun `a new detection replaces the previous one`() {
        sticky.onDetected("de", "auto")
        assertEquals("de", sticky.requestLanguage("auto"))
        sticky.onDetected("ja", "auto")
        assertEquals("ja", sticky.requestLanguage("auto"))
    }

    @Test
    fun `explicit language always wins and is never overwritten by detection`() {
        sticky.onDetected("de", "auto")
        assertEquals("en", sticky.requestLanguage("en"))
        sticky.onDetected("cs", "en")
        assertEquals("en", sticky.requestLanguage("en"))
        assertEquals("de", sticky.requestLanguage("auto"))
    }

    @Test
    fun `unknown or auto detections are ignored`() {
        sticky.onDetected("xx", "auto")
        assertNull(sticky.requestLanguage("auto"))
        sticky.onDetected("auto", "auto")
        assertNull(sticky.requestLanguage("auto"))
        sticky.onDetected("  ", "auto")
        assertNull(sticky.requestLanguage("auto"))
    }

    @Test
    fun `reset clears the sticky language`() {
        sticky.onDetected("cs", "auto")
        assertEquals("cs", sticky.requestLanguage("auto"))
        sticky.reset()
        assertNull(sticky.requestLanguage("auto"))
    }
}
