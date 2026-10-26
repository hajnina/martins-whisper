package com.martins.whisper.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlagsTest {

    @After
    fun tearDown() {
        Flags.reset()
    }

    @Test
    fun `every feature flag is OFF by default so trunk stays releasable`() {
        Flags.reset()
        val values = Flags.snapshot()
        assertEquals(4, values.size)
        assertTrue("flags must default OFF: $values", values.values.none { it })
        assertFalse(Flags.onboardingV2)
        assertFalse(Flags.languagesV2)
        assertFalse(Flags.gemmaEngine)
        assertFalse(Flags.accelBackends)
    }

    @Test
    fun `enabling one flag does not leak into the others`() {
        Flags.reset()
        Flags.languagesV2 = true

        assertTrue(Flags.languagesV2)
        assertFalse(Flags.onboardingV2)
        assertFalse(Flags.gemmaEngine)
        assertFalse(Flags.accelBackends)
        assertEquals(
            mapOf(
                "onboardingV2" to false,
                "languagesV2" to true,
                "gemmaEngine" to false,
                "accelBackends" to false,
            ),
            Flags.snapshot(),
        )
    }

    @Test
    fun `reset restores the default-OFF contract`() {
        Flags.onboardingV2 = true
        Flags.gemmaEngine = true

        Flags.reset()

        assertTrue(Flags.snapshot().values.none { it })
    }
}
