package com.martins.whisper.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class TextNormalizerEdgeTest {

    private val emoji = "\uD83D\uDE00"
    private val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"
    private val arabic = "مَرْحَبًا بِالْعَالَمِ"
    private val hebrew = "שָׁלוֹם עוֹלָם"

    @Test
    fun `blank and unicode whitespace only input becomes empty`() {
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize(" \t\n\r\u000B\u000C"))
        assertEquals("", TextNormalizer.normalize("\u00A0\u3000"))
    }

    @Test
    fun `rtl text and combining marks are preserved`() {
        assertEquals("مَرْحَبًا بِالْعَالَمِ", TextNormalizer.normalize("  مَرْحَبًا   بِالْعَالَمِ  "))
        assertEquals("שָׁלוֹם עוֹלָם", TextNormalizer.normalize("שָׁלוֹם \t עוֹלָם"))
    }

    @Test
    fun `ascii punctuation spacing is removed around rtl text`() {
        assertEquals("مرحبا, بالعالم.", TextNormalizer.normalize("مرحبا ,  بالعالم ."))
        assertEquals("שלום, עולם!", TextNormalizer.normalize("שלום , עולם !"))
    }

    @Test
    fun `emoji and zwj sequences survive whitespace normalization`() {
        assertEquals(
            "$family hello $emoji",
            TextNormalizer.normalize("  $family   hello \t $emoji  "),
        )
    }

    @Test
    fun `hallucinations are dropped after whitespace and punctuation normalization`() {
        assertEquals("", TextNormalizer.normalize("  Thank   you .  "))
        assertEquals("", TextNormalizer.normalize("thank\tyou"))
        assertEquals("", TextNormalizer.normalize(" [ BLANK_AUDIO ] "))
        assertEquals("", TextNormalizer.normalize("Thanks \n for \n watching !"))
    }

    @Test
    fun `hallucination words inside real speech are kept`() {
        assertEquals(
            "Thank you. Now continue.",
            TextNormalizer.normalize("Thank you.  Now continue."),
        )
    }

    @Test
    fun `repeated punctuation is collapsed and spacing normalized`() {
        assertEquals("What?! Really...", TextNormalizer.normalize("What ? !  Really . . ."))
        assertEquals("a, b; c: d!", TextNormalizer.normalize("a ,  b ; c :  d !"))
    }

    @Test
    fun `large mixed whitespace input collapses deterministically`() {
        val random = Random(424242L)
        val separators = listOf(" ", "  ", "\t", "\n", " \r\n ", "   ")
        val words = (0 until 50_000).map { "w$it" }
        val text = buildString {
            words.forEachIndexed { index, word ->
                if (index > 0) append(separators[random.nextInt(separators.size)])
                append(word)
            }
        }
        val normalized = TextNormalizer.normalize(text)
        assertEquals(words.joinToString(" "), normalized)
        assertFalse(normalized.contains("  "))
        assertFalse(normalized.startsWith(" "))
        assertFalse(normalized.endsWith(" "))
    }

    @Test
    fun `large already normalized text is unchanged`() {
        val normalized = "word ".repeat(200_000).trim()
        assertEquals(normalized, TextNormalizer.normalize(normalized))
    }

    @Test
    fun `seeded random token stream normalizes to single spaces`() {
        val random = Random(20261026L)
        val tokens = listOf("hello", "world", arabic, hebrew, emoji, family, "line")
        repeat(500) {
            val count = random.nextInt(200)
            val words = (0 until count).map { tokens[random.nextInt(tokens.size)] }
            val separators = listOf(" ", "\t", "\n", " \r\n ")
            val text = buildString {
                words.forEachIndexed { index, word ->
                    if (index > 0) append(separators[random.nextInt(separators.size)])
                    append(word)
                }
            }
            val normalized = TextNormalizer.normalize(text)
            assertEquals(words.joinToString(" "), normalized)
            assertTrue(normalized.isEmpty() || !normalized.startsWith(" "))
            assertTrue(normalized.isEmpty() || !normalized.endsWith(" "))
            assertFalse(normalized.contains("  "))
        }
    }
}
