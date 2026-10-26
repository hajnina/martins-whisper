package com.martins.whisper.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphemesTest {

    @Test
    fun `combining marks stay with their base`() {
        val text = "e\u0301x"
        assertFalse(Graphemes.isBoundary(text, 1))
        assertTrue(Graphemes.isBoundary(text, 2))
        assertEquals(0, Graphemes.clusterStart(text, 1))
        assertEquals(2, Graphemes.clusterEnd(text, 1))
    }

    @Test
    fun `surrogate pairs are never split`() {
        val text = "a\uD83D\uDE00b"
        assertFalse(Graphemes.isBoundary(text, 2))
        assertEquals(1, Graphemes.clusterStart(text, 2))
        assertEquals(3, Graphemes.clusterEnd(text, 2))
    }

    @Test
    fun `zwj sequences are one cluster`() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"
        for (index in 1 until family.length) {
            assertFalse("index $index", Graphemes.isBoundary(family, index))
        }
        assertEquals(0, Graphemes.clusterStart(family, family.length - 1))
        assertEquals(family.length, Graphemes.clusterEnd(family, 1))
    }

    @Test
    fun `emoji modifiers and variation selectors stay with their base`() {
        val thumbs = "\uD83D\uDC4D\uD83C\uDFFD"
        assertFalse(Graphemes.isBoundary(thumbs, 2))
        assertEquals(0, Graphemes.clusterStart(thumbs, 2))
        assertEquals(thumbs.length, Graphemes.clusterEnd(thumbs, 2))

        val keycap = "1\uFE0F\u20E3"
        assertFalse(Graphemes.isBoundary(keycap, 1))
        assertFalse(Graphemes.isBoundary(keycap, 2))
        assertEquals(0, Graphemes.clusterStart(keycap, 2))
        assertEquals(keycap.length, Graphemes.clusterEnd(keycap, 1))
    }

    @Test
    fun `regional indicator flags pair up`() {
        val flags = "\uD83C\uDDE8\uD83C\uDDFF\uD83C\uDDF8\uD83C\uDDF0"
        assertTrue(Graphemes.isBoundary(flags, 0))
        assertFalse(Graphemes.isBoundary(flags, 2))
        assertTrue(Graphemes.isBoundary(flags, 4))
        assertFalse(Graphemes.isBoundary(flags, 6))
        assertTrue(Graphemes.isBoundary(flags, 8))
        assertEquals(0, Graphemes.clusterStart(flags, 2))
        assertEquals(4, Graphemes.clusterEnd(flags, 2))
    }

    @Test
    fun `devanagari and thai combining marks stay with their cluster`() {
        val hindi = "नमस्ते"
        assertFalse(Graphemes.isBoundary(hindi, 3))
        assertEquals(2, Graphemes.clusterStart(hindi, 3))
        assertEquals(4, Graphemes.clusterEnd(hindi, 3))

        val thai = "กิ"
        assertFalse(Graphemes.isBoundary(thai, 1))
        assertEquals(0, Graphemes.clusterStart(thai, 1))
        assertEquals(2, Graphemes.clusterEnd(thai, 1))
    }

    @Test
    fun `ordinary text boundaries are untouched`() {
        val text = "hello world"
        for (index in 0..text.length) {
            assertTrue("index $index", Graphemes.isBoundary(text, index))
        }
        assertEquals(5, Graphemes.clusterStart(text, 5))
        assertEquals(5, Graphemes.clusterEnd(text, 5))
    }
}
