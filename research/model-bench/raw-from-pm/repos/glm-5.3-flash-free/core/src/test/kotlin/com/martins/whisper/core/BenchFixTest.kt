package com.martins.whisper.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BenchFixTest {
    @Test
    fun addsTwoNumbers() {
        assertEquals(7, BenchFix.add(3, 4))
    }

    @Test
    fun handlesNegative() {
        assertEquals(1, BenchFix.add(4, -3))
    }
}
