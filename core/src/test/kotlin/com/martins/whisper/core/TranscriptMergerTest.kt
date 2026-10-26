package com.martins.whisper.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TranscriptMergerTest {
    @Test
    fun `removes duplicated boundary words`() {
        assertEquals(
            "Hello world again",
            TranscriptMerger.merge(listOf("Hello world", "world again")),
        )
    }

    @Test
    fun `dedupe is case insensitive and keeps richer punctuation`() {
        assertEquals(
            "Hello, World! Again",
            TranscriptMerger.merge(listOf("Hello, World!", "world! Again")),
        )
        assertEquals(
            "hello, world",
            TranscriptMerger.merge(listOf("hello", "hello, world")),
        )
    }

    @Test
    fun `dedupe is limited to eight words`() {
        val first = "a b c d e f g h"
        val second = "a b c d e f g h i"
        assertEquals(
            "a b c d e f g h i",
            TranscriptMerger.merge(listOf(first, second)),
        )
    }

    @Test
    fun `zero overlap does not remove words`() {
        assertEquals(
            "hello world world again",
            TranscriptMerger.merge(listOf("hello world", "world again"), listOf(0)),
        )
    }

    @Test
    fun `positive overlap removes words`() {
        assertEquals(
            "hello world again",
            TranscriptMerger.merge(listOf("hello world", "world again"), listOf(250)),
        )
    }

    @Test
    fun `empty and blank parts are ignored`() {
        assertEquals("", TranscriptMerger.merge(listOf("", "   ", "\n")))
        assertEquals("hello world", TranscriptMerger.merge(listOf("", " hello ", "world")))
    }

    @Test
    fun `does not invent text`() {
        assertEquals("one two three", TranscriptMerger.merge(listOf("one two three")))
        assertEquals("one two three", TranscriptMerger.merge(listOf("one two", "three")))
    }
}
