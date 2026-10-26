package com.martins.whisper.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleFormatTest {

    @Test
    fun `elapsed formatter pads seconds`() {
        assertEquals("0:00", BubbleFormat.elapsed(0))
        assertEquals("0:00", BubbleFormat.elapsed(-500))
        assertEquals("0:04", BubbleFormat.elapsed(4_900))
        assertEquals("1:05", BubbleFormat.elapsed(65_000))
        assertEquals("10:00", BubbleFormat.elapsed(600_000))
    }
}
