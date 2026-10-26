package com.martins.whisper.core

import com.martins.whisper.core.text.TextNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals

class TextNormalizerTest {
    @Test
    fun `normalizes whitespace and trims`() {
        assertEquals("Hello world", TextNormalizer.normalize("  Hello   world  "))
        assertEquals("a b c", TextNormalizer.normalize("a\tb\nc"))
    }

    @Test
    fun `removes space before punctuation`() {
        assertEquals("Hello, world.", TextNormalizer.normalize("Hello ,  world ."))
    }

    @Test
    fun `blank text becomes empty`() {
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize("   \n\t "))
    }

    @Test
    fun `drops known hallucinations`() {
        assertEquals("", TextNormalizer.normalize("Thank you."))
        assertEquals("", TextNormalizer.normalize(" thank you "))
        assertEquals("", TextNormalizer.normalize("[BLANK_AUDIO]"))
        assertEquals("", TextNormalizer.normalize("(silence)"))
        assertEquals("", TextNormalizer.normalize("Thanks for watching!"))
    }

    @Test
    fun `keeps normal speech`() {
        assertEquals("Please transcribe this.", TextNormalizer.normalize(" Please transcribe this. "))
    }
}
