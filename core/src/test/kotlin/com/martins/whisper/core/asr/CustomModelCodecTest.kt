package com.martins.whisper.core.asr

import com.martins.whisper.core.ModelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomModelCodecTest {

    private val custom = ModelSpec(
        id = "custom-weird-name",
        displayName = "Weird | name\nwith newline \u00e4\u00f6\u00fc",
        fileName = "weird name.bin",
        url = null,
        sha256 = "ab".repeat(32),
        sizeBytes = 123_456L,
        multilingual = false,
        notes = "Custom imported model.",
    )

    @Test
    fun `round trips every persisted field`() {
        val encoded = CustomModelCodec.encode(listOf(custom))
        assertEquals(listOf(custom), CustomModelCodec.decode(encoded))
    }

    @Test
    fun `empty and malformed payloads decode to an empty list`() {
        assertTrue(CustomModelCodec.decode(null).isEmpty())
        assertTrue(CustomModelCodec.decode("").isEmpty())
        assertTrue(CustomModelCodec.decode("garbage").isEmpty())
        assertTrue(CustomModelCodec.decode("v1|broken").isEmpty())
        assertTrue(CustomModelCodec.decode(CustomModelCodec.encode(listOf(custom)) + "\nnot-a-record").size == 1)
    }

    @Test
    fun `duplicate ids keep the first record`() {
        val second = custom.copy(displayName = "second")
        val decoded = CustomModelCodec.decode(
            CustomModelCodec.encode(listOf(custom, second)),
        )
        assertEquals(listOf(custom), decoded)
    }

    @Test
    fun `a corrupt checksum is rejected instead of persisted`() {
        val encoded = CustomModelCodec.encode(listOf(custom)).replace(custom.sha256!!, "zz")
        assertTrue(CustomModelCodec.decode(encoded).isEmpty())
    }
}
