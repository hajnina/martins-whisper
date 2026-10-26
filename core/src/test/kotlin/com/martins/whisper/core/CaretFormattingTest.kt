package com.martins.whisper.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CaretFormattingTest {

    private fun format(insert: String, current: String?, caret: Int = current?.length ?: 0) =
        TextInsertionPlanner.formatForCaret(insert, current, caret)

    @Test
    fun `whisper leading and trailing spaces are trimmed`() {
        assertEquals("Hello", format("  Hello  ", null))
    }

    @Test
    fun `start of field starts with a capital and no leading space`() {
        assertEquals("Hello there", format("hello there", ""))
        assertEquals("Hello there", format("hello there", "   "))
    }

    @Test
    fun `mid sentence is lowercased and gets one leading space`() {
        assertEquals(" the cat", format("The cat", "I saw"))
        assertEquals("the cat", format(" the cat", "I saw "))
        assertEquals("the cat", format("the cat", "I saw  "))
    }

    @Test
    fun `start of a new sentence keeps the capital and gets a space`() {
        assertEquals(" The cat", format("the cat", "Done."))
        assertEquals("The cat", format("the cat", "Done. "))
        assertEquals("Next line", format("next line", "First line\n"))
    }

    @Test
    fun `replacing a selection uses the character before the selection`() {
        assertEquals("New", format("new", "old", 0))
        assertEquals("new", format("new", "a old", 2))
        assertEquals("New", format("new", "Done. old", 6))
    }

    @Test
    fun `czech diacritics keep their case rules`() {
        assertEquals("jak se máš", format("jak se máš", "Ahoj, "))
        assertEquals("Jak se máš", format("jak se máš", "Ahoj. "))
        assertEquals("Jak se máš", format("jak se máš", null))
    }

    @Test
    fun `non letter starts are left alone`() {
        assertEquals(" 42", format("42", "The answer is"))
        assertEquals(" ...", format("...", "wait"))
    }

    @Test
    fun `empty insert stays empty`() {
        assertEquals("", format("   ", "Hello"))
    }
}
