package com.martins.whisper.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class TextInsertionPlannerEdgeTest {

    private val emoji = "\uD83D\uDE00"
    private val grin = "\uD83D\uDE01"
    private val man = "\uD83D\uDC68"
    private val woman = "\uD83D\uDC69"
    private val girl = "\uD83D\uDC67"
    private val boy = "\uD83D\uDC66"
    private val zwj = "\u200D"
    private val family = man + zwj + woman + zwj + girl + zwj + boy
    private val cjkB = "\uD840\uDC00"
    private val arabic = "مَرْحَبًا بِالْعَالَمِ"
    private val hebrew = "שָׁלוֹם עוֹלָם"

    private fun insert(
        current: String?,
        start: Int,
        end: Int,
        text: String,
    ): TextInsertionPlanner.Result =
        TextInsertionPlanner.insert(current, start, end, text)

    private fun String.isWellFormedUtf16(): Boolean {
        var index = 0
        while (index < length) {
            val current = this[index]
            if (current.isHighSurrogate()) {
                if (index + 1 >= length || !this[index + 1].isLowSurrogate()) return false
                index += 2
            } else if (current.isLowSurrogate()) {
                return false
            } else {
                index++
            }
        }
        return true
    }

    private fun reference(
        base: String,
        selectionStart: Int,
        selectionEnd: Int,
        insert: String,
    ): Pair<String, Int> {
        val rawStart = selectionStart.coerceIn(0, base.length)
        val rawEnd = selectionEnd.coerceIn(rawStart, base.length)
        val start = if (base.splitsPairAt(rawStart)) rawStart - 1 else rawStart
        val end = if (base.splitsPairAt(rawEnd)) rawEnd + 1 else rawEnd
        val boundedEnd = maxOf(start, end)
        return (base.substring(0, start) + insert + base.substring(boundedEnd)) to
            (start + insert.length)
    }

    private fun String.splitsPairAt(index: Int): Boolean =
        index in 1 until length &&
            this[index - 1].isHighSurrogate() &&
            this[index].isLowSurrogate()

    private fun assertWellFormed(result: TextInsertionPlanner.Result) {
        assertTrue("result = ${result.text}", result.text.isWellFormedUtf16())
    }

    @Test
    fun `null field with out of range selection and empty insert is a no op`() {
        val result = insert(null, Int.MIN_VALUE, Int.MAX_VALUE, "")
        assertEquals("", result.text)
        assertEquals(0, result.selectionStart)
        assertEquals(0, result.selectionEnd)
    }

    @Test
    fun `empty field accepts inserts at every clamp`() {
        val cases = listOf(
            Triple(Int.MIN_VALUE, Int.MIN_VALUE, 1),
            Triple(Int.MAX_VALUE, Int.MAX_VALUE, 1),
            Triple(-10, 10, 1),
            Triple(10, -10, 1),
        )
        for ((start, end, caret) in cases) {
            val result = insert("", start, end, "x")
            assertEquals("x", result.text)
            assertEquals(caret, result.selectionStart)
            assertEquals(caret, result.selectionEnd)
        }
    }

    @Test
    fun `empty insert at start end and middle leaves text intact`() {
        val base = "a$emoji$cjkB$arabic$hebrew"
        val positions = listOf(0, base.length, base.length / 2)
        for (position in positions) {
            val result = insert(base, position, position, "")
            assertEquals(base, result.text)
            assertEquals(position, result.selectionStart)
            assertEquals(position, result.selectionEnd)
        }
    }

    @Test
    fun `cjk extension b surrogate pair is replaced whole when caret is inside`() {
        val base = "A$cjkB" + "B"
        assertEquals("A|B", insert(base, 2, 2, "|").text)
        assertEquals("A|$cjkB" + "B", insert(base, 1, 1, "|").text)
        assertEquals("A$cjkB|B", insert(base, 3, 3, "|").text)
    }

    @Test
    fun `cjk extension b selection entirely inside pair replaces the code point`() {
        val base = "$emoji$cjkB$grin"
        val result = insert(base, 3, 4, "X")
        assertEquals("${emoji}X$grin", result.text)
        assertEquals(3, result.selectionStart)
        assertWellFormed(result)
    }

    @Test
    fun `zwj family insertions never leave lone surrogates`() {
        for (position in 0..family.length) {
            val result = insert(family, position, position, "|")
            assertWellFormed(result)
            assertTrue(result.selectionStart in 0..result.text.length)
            assertEquals(result.selectionStart, result.selectionEnd)
        }
    }

    @Test
    fun `zwj cluster boundaries are code unit boundaries not surrogate pairs`() {
        val result = insert(family, man.length, man.length, "|")
        assertEquals(man + "|" + zwj + woman + zwj + girl + zwj + boy, result.text)
    }

    @Test
    fun `combining mark boundaries remain code unit boundaries`() {
        val base = "e\u0301"
        assertEquals("e|\u0301", insert(base, 1, 1, "|").text)
        assertEquals("e\u0301|", insert(base, 2, 2, "|").text)
        assertEquals("|e\u0301", insert(base, 0, 0, "|").text)
    }

    @Test
    fun `rtl text insertion preserves code unit order`() {
        val atStart = insert(arabic, 0, 0, "|")
        assertEquals("|$arabic", atStart.text)
        assertEquals(1, atStart.selectionStart)

        val atEnd = insert(arabic, arabic.length, arabic.length, "|")
        assertEquals("$arabic|", atEnd.text)
        assertEquals(arabic.length + 1, atEnd.selectionStart)

        val space = arabic.indexOf(' ')
        val beforeWord = insert(arabic, space, space, "|")
        assertEquals(arabic.substring(0, space) + "|" + arabic.substring(space), beforeWord.text)

        val full = insert(arabic, 0, arabic.length, hebrew)
        assertEquals(hebrew, full.text)
        assertEquals(hebrew.length, full.selectionStart)
    }

    @Test
    fun `rtl selection of a combining mark replaces just that mark`() {
        val base = "שָׁלוֹם"
        val markStart = base.indexOf('\u05B8')
        val result = insert(base, markStart, markStart + 1, "")
        assertEquals(base.removeRange(markStart, markStart + 1), result.text)
        assertEquals(markStart, result.selectionStart)
        assertWellFormed(result)
    }

    @Test
    fun `very large input inserts at start end and a safe middle`() {
        val unit = "a$emoji$cjkB\u0301م"
        val base = unit.repeat(200_000)
        val atStart = insert(base, 0, 0, "X")
        assertEquals("X$base", atStart.text)
        assertEquals(1, atStart.selectionStart)

        val atEnd = insert(base, base.length, base.length, "Y")
        assertEquals("${base}Y", atEnd.text)
        assertEquals(base.length + 1, atEnd.selectionStart)

        val middle = (base.length / 2 / unit.length) * unit.length + 1
        val midResult = insert(base, middle, middle, "Z")
        assertEquals(base.substring(0, middle) + "Z" + base.substring(middle), midResult.text)
        assertEquals(middle + 1, midResult.selectionStart)
        assertWellFormed(midResult)
    }

    @Test
    fun `invalid extreme selections match the documented clamp for every field`() {
        val fields = listOf("", "a", "abc", "a${emoji}b", "$cjkB", arabic, hebrew)
        val selections = listOf(
            Int.MIN_VALUE to Int.MIN_VALUE,
            Int.MIN_VALUE to Int.MAX_VALUE,
            Int.MAX_VALUE to Int.MIN_VALUE,
            Int.MAX_VALUE to Int.MAX_VALUE,
            -1 to 0,
            0 to -1,
        )
        for (field in fields) {
            for ((start, end) in selections) {
                val result = insert(field, start, end, "X")
                val (expectedText, expectedCaret) = reference(field, start, end, "X")
                assertEquals(expectedText, result.text)
                assertEquals(expectedCaret, result.selectionStart)
                assertEquals(expectedCaret, result.selectionEnd)
                assertWellFormed(result)
            }
        }
    }

    @Test
    fun `seeded random selections clamp and never split surrogate pairs`() {
        val random = Random(20261026L)
        val atoms = listOf(
            "a",
            "B",
            " ",
            emoji,
            grin,
            cjkB,
            family,
            "\u0645\u064E",
            "\u05E9\u05B8",
            "\u0301",
            "!",
            "z",
        )
        val inserts = listOf("", "X", emoji, cjkB, arabic, hebrew, " ")
        repeat(3_000) {
            val base = buildString {
                repeat(random.nextInt(40)) {
                    append(atoms[random.nextInt(atoms.size)])
                }
            }
            val insert = inserts[random.nextInt(inserts.size)]
            val start = random.nextInt(base.length * 2 + 21) - 10
            val end = random.nextInt(base.length * 2 + 21) - 10
            val result = insert(base, start, end, insert)
            val (expectedText, expectedCaret) = reference(base, start, end, insert)
            val label = "base length=${base.length} start=$start end=$end"
            assertEquals(label, expectedText, result.text)
            assertEquals(label, expectedCaret, result.selectionStart)
            assertEquals(label, expectedCaret, result.selectionEnd)
            assertTrue(label, result.selectionStart in 0..result.text.length)
            assertWellFormed(result)
        }
    }
}
