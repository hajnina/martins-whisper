package com.martins.whisper.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeUtf8Test {

    @Test
    fun decodesCzechDiacriticsAndEmojiFromNativeBytes() {
        val original = "Příliš žluťoučký kůň úpěl ďábelské ódy 🎤🇨🇿"
        val fromNative = original.toByteArray(Charsets.UTF_8)

        assertEquals(original, decodeNativeUtf8(fromNative))
    }

    @Test
    fun replacesMalformedSequenceInsteadOfFailing() {
        val splitSequence = byteArrayOf(0x41, 0xC3.toByte(), 0x28)

        val decoded = decodeNativeUtf8(splitSequence)

        assertTrue(decoded.startsWith("A"))
        assertTrue(decoded.contains('\uFFFD'))
    }
}
