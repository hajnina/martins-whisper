package com.martins.whisper.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Script-by-script acceptance for fitting dictated text into a field: CJK/Thai must get neither an
 * inserted space nor capitalisation, RTL and Indic scripts keep spaces but are never re-cased,
 * cased scripts keep the caret rules.
 */
class ScriptInsertionTest {

    private fun format(
        insert: String,
        current: String?,
        caret: Int = current?.length ?: 0,
        language: String?,
    ) = TextInsertionPlanner.formatForCaret(insert, current, caret, language)

    @Test
    fun `czech keeps diacritics and sentence case`() {
        assertEquals("příliš žluťoučký kůň", format("příliš žluťoučký kůň", "Ahoj, ", 6, "cs"))
        assertEquals("Příliš žluťoučký kůň", format("příliš žluťoučký kůň", "Ahoj. ", 6, "cs"))
        assertEquals("Příliš žluťoučký kůň", format("příliš žluťoučký kůň", null, 0, "cs"))
    }

    @Test
    fun `german adds a space and respects sentence case`() {
        assertEquals(" das ist gut", format("das ist gut", "Heute", 5, "de"))
        assertEquals("Das ist gut", format("das ist gut", "Heute. ", 7, "de"))
        assertEquals("Das ist gut", format("das ist gut", null, 0, "de"))
    }

    @Test
    fun `russian capitalises at the start and mid sentence keeps lower case`() {
        assertEquals(" привет мир", format("привет мир", "Я сказал", 8, "ru"))
        assertEquals("Привет мир", format("привет мир", null, 0, "ru"))
        assertEquals("Привет мир", format("привет мир", "Я сказал. ", 10, "ru"))
    }

    @Test
    fun `greek capitalises at the start and mid sentence keeps lower case`() {
        assertEquals(" καλημέρα", format("καλημέρα", "Είπα", 4, "el"))
        assertEquals("Καλημέρα", format("καλημέρα", null, 0, "el"))
    }

    @Test
    fun `arabic gets a word space but is never capitalised or reordered`() {
        assertEquals(" مرحبا بالعالم", format("مرحبا بالعالم", "قلت", 3, "ar"))
        assertEquals("مرحبا بالعالم", format("مرحبا بالعالم", null, 0, "ar"))
        assertEquals("مرحبا بالعالم", format("مرحبا بالعالم", "قلت. ", 5, "ar"))
    }

    @Test
    fun `hebrew gets a word space and keeps its own case`() {
        assertEquals(" שלום עולם", format("שלום עולם", "אמרתי", 5, "he"))
        assertEquals("שלום עולם", format("שלום עולם", null, 0, "he"))
    }

    @Test
    fun `hindi keeps combining marks and gets a word space without case`() {
        assertEquals(" नमस्ते दुनिया", format("नमस्ते दुनिया", "मैंने कहा", 8, "hi"))
        assertEquals("नमस्ते दुनिया", format("नमस्ते दुनिया", null, 0, "hi"))
    }

    @Test
    fun `thai gets neither a space nor capitalisation`() {
        assertEquals("สวัสดีชาวโลก", format("สวัสดีชาวโลก", "ฉันพูด", 6, "th"))
        assertEquals("สวัสดีชาวโลก", format("สวัสดีชาวโลก", null, 0, "th"))
    }

    @Test
    fun `japanese gets neither a space nor capitalisation`() {
        assertEquals("こんにちは世界", format("こんにちは世界", "今日は", 3, "ja"))
        assertEquals("こんにちは世界", format("こんにちは世界", null, 0, "ja"))
    }

    @Test
    fun `chinese gets neither a space nor capitalisation`() {
        assertEquals("你好世界", format("你好世界", "我说", 2, "zh"))
        assertEquals("你好世界", format("你好世界", null, 0, "zh"))
        assertEquals("你好世界", format("你好世界", "我說", 2, "yue"))
    }

    @Test
    fun `korean gets a word space without case`() {
        assertEquals(" 안녕하세요 세계", format("안녕하세요 세계", "저는", 2, "ko"))
        assertEquals("안녕하세요 세계", format("안녕하세요 세계", null, 0, "ko"))
    }

    @Test
    fun `emoji is preserved and capitalisation applies to the first letter after it`() {
        assertEquals("😀 Hello", format("😀 hello", null, 0, "en"))
        assertEquals(" 😀 hello", format("😀 hello", "Hi", 2, "en"))
        assertEquals(" 😀", format("😀", "Hi", 2, "en"))
        assertEquals("😀", format("😀", null, 0, null))
    }

    @Test
    fun `language may be inferred from the text when auto`() {
        assertEquals(" привет", format("привет", "Я", 1, null))
        assertEquals("你好", format("你好", "我说", 2, null))
        assertEquals("สวัสดี", format("สวัสดี", "ฉันพูด", 6, null))
        assertEquals(" مرحبا", format("مرحبا", "قلت", 3, null))
    }

    @Test
    fun `whisper padding is trimmed including non breaking and ideographic spaces`() {
        assertEquals("Hello", format("\u00A0 Hello \u3000", null, 0, "en"))
        assertEquals("你好", format("\u3000你好\u00A0", null, 0, "zh"))
    }

    @Test
    fun `read back comparison normalises unicode to nfc`() {
        assertTrue(TextInsertionPlanner.normalizedEquals("café", "cafe\u0301"))
        assertTrue(TextInsertionPlanner.normalizedEquals("Příliš", "Příliš"))
        assertTrue(TextInsertionPlanner.normalizedEquals("नमस्ते", "नमस्ते"))
        assertFalse(TextInsertionPlanner.normalizedEquals("café", "cafè"))
        assertFalse(TextInsertionPlanner.normalizedEquals(null, "café"))
        assertEquals("café", TextInsertionPlanner.normalizeNfc("cafe\u0301"))
    }

    @Test
    fun `insertion never splits a combining mark cluster`() {
        val hindi = "नमस्ते"
        assertEquals("नमXते", TextInsertionPlanner.insert(hindi, 3, 3, "X").text)
        val thai = "กิ"
        assertEquals("X", TextInsertionPlanner.insert(thai, 1, 1, "X").text)
        assertEquals("กิ|", TextInsertionPlanner.insert(thai, 2, 2, "|").text)
    }

    @Test
    fun `insertion never splits an rtl combining cluster`() {
        val arabic = "مَ"
        assertEquals("X", TextInsertionPlanner.insert(arabic, 1, 1, "X").text)
        assertEquals("مَ|", TextInsertionPlanner.insert(arabic, 2, 2, "|").text)
        val hebrew = "שָׁ"
        assertEquals("X", TextInsertionPlanner.insert(hebrew, 1, 1, "X").text)
    }

    @Test
    fun `insertion never splits an emoji surrogate pair or zwj sequence`() {
        assertEquals("X", TextInsertionPlanner.insert("😀", 1, 1, "X").text)
        assertEquals("😀X", TextInsertionPlanner.insert("😀", 2, 2, "X").text)
        val family = "👨\u200D👩\u200D👧\u200D👦"
        assertEquals("X", TextInsertionPlanner.insert(family, 2, 2, "X").text)
        assertEquals("X", TextInsertionPlanner.insert(family, family.length - 1, family.length - 1, "X").text)
    }

    @Test
    fun `insertion preserves nfc and nfd input and read back still matches`() {
        val nfc = "café"
        val nfd = "cafe\u0301"
        assertEquals("café", TextInsertionPlanner.insert("", 0, 0, nfc).text)
        assertEquals(nfd, TextInsertionPlanner.insert("", 0, 0, nfd).text)
        assertTrue(
            TextInsertionPlanner.normalizedEquals(
                TextInsertionPlanner.insert("", 0, 0, nfd).text,
                nfc,
            ),
        )
    }
}
