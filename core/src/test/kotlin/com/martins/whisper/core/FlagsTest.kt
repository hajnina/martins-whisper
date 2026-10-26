package com.martins.whisper.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlagsTest {

    @Test
    fun `unfinished features are off by default`() {
        assertFalse(Flags.onboardingV2)
        assertFalse(Flags.gemmaEngine)
        assertFalse(Flags.accelBackends)
    }

    @Test
    fun `a build or test can turn one feature on without touching the others`() {
        Flags.onboardingV2 = true
        try {
            assertTrue(Flags.onboardingV2)
            assertFalse(Flags.gemmaEngine)
        } finally {
            Flags.onboardingV2 = false
        }
    }
}
