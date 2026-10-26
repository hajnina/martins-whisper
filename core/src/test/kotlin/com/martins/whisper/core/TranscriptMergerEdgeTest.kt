package com.martins.whisper.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptMergerEdgeTest {

    @Test
    fun `empty list all blank parts and oversized overlap list merge to empty`() {
        assertEquals("", TranscriptMerger.merge(emptyList()))
        assertEquals("", TranscriptMerger.merge(listOf("", "   ", "\n\t", "")))
        assertEquals(
            "",
            TranscriptMerger.merge(listOf("", "", ""), listOf(250, Int.MAX_VALUE, -1)),
        )
    }

    @Test
    fun `single part trims and uses single spaces`() {
        assertEquals("hello world", TranscriptMerger.merge(listOf("  hello   world \n")))
        assertEquals("...", TranscriptMerger.merge(listOf("...")))
    }

    @Test
    fun `explicit empty overlap list dedupes every boundary`() {
        assertEquals(
            "hello world again",
            TranscriptMerger.merge(listOf("hello world", "world again"), emptyList()),
        )
    }

    @Test
    fun `overlap list longer than the boundary count is ignored`() {
        assertEquals(
            "hello world again",
            TranscriptMerger.merge(listOf("hello world", "world again"), listOf(250, 999, 999)),
        )
    }

    @Test
    fun `missing overlap entries mean no dedupe at later boundaries`() {
        assertEquals(
            "a b c c d",
            TranscriptMerger.merge(listOf("a b", "b c", "c d"), listOf(250)),
        )
    }

    @Test
    fun `negative overlap disables dedupe at that boundary`() {
        assertEquals(
            "hello world world again",
            TranscriptMerger.merge(listOf("hello world", "world again"), listOf(-250)),
        )
        assertEquals(
            "a b c c d",
            TranscriptMerger.merge(listOf("a b", "b c", "c d"), listOf(250, -1)),
        )
    }

    @Test
    fun `non monotonic overlaps dedupe only the enabled boundaries`() {
        assertEquals(
            "a b c c d e",
            TranscriptMerger.merge(
                listOf("a b", "b c", "c d", "d e"),
                listOf(250, 0, 250),
            ),
        )
    }

    @Test
    fun `oversized overlap value still dedupes an eight word boundary`() {
        assertEquals(
            "a b c d e f g h i",
            TranscriptMerger.merge(
                listOf("a b c d e f g h", "a b c d e f g h i"),
                listOf(Int.MAX_VALUE),
            ),
        )
    }

    @Test
    fun `blank parts are skipped while raw overlap positions are preserved`() {
        assertEquals(
            "hello world",
            TranscriptMerger.merge(
                listOf("", "hello", "", "hello world"),
                listOf(250, 250, 250),
            ),
        )
    }

    @Test
    fun `rtl boundary words are deduped and first diacritics are kept`() {
        assertEquals(
            "שלום עולם חדש",
            TranscriptMerger.merge(listOf("שלום עולם", "עולם חדש")),
        )
        val first = "مَرْحَبًا بِكَ"
        assertEquals(
            "$first يَا صَدِيقِي",
            TranscriptMerger.merge(listOf(first, "بِكَ يَا صَدِيقِي")),
        )
        assertEquals(
            "שָׁלוֹם עולם",
            TranscriptMerger.merge(listOf("שָׁלוֹם", "שלום עולם")),
        )
    }

    @Test
    fun `apostrophes and identical emoji boundary tokens are deduped`() {
        assertEquals(
            "don't stop now",
            TranscriptMerger.merge(listOf("don't stop", "stop now")),
        )
        val smile = "\uD83D\uDE00"
        assertEquals(
            "hi $smile there",
            TranscriptMerger.merge(listOf("hi $smile", "$smile there")),
        )
    }

    @Test
    fun `ten thousand overlapping parts merge without losing tokens`() {
        val count = 10_000
        val parts = (0 until count).map { "w$it w${it + 1}" }
        val merged = TranscriptMerger.merge(parts)
        assertEquals(
            (0..count).joinToString(" ") { "w$it" },
            merged,
        )
    }

    @Test
    fun `huge single part is tokenized and joined once`() {
        val count = 100_000
        val words = (0 until count).map { "w$it" }
        val merged = TranscriptMerger.merge(listOf(words.joinToString("   ")))
        assertEquals(words.joinToString(" "), merged)
    }
}
