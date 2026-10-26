package com.martins.whisper.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEnergyTest {

    @Test
    fun `digital silence has zero peak and rms`() {
        val energy = AudioEnergy.of(FloatArray(320))
        assertTrue(energy.isDigitalSilence)
        assertEquals(0f, energy.peak, 0f)
        assertEquals(0f, energy.rms, 0f)
    }

    @Test
    fun `peak and rms match literal samples`() {
        val energy = AudioEnergy.of(floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f))
        assertEquals(1f, energy.peak, 0f)
        assertEquals(0.70710677f, energy.rms, 1e-5f)
        assertFalse(energy.isDigitalSilence)
    }

    @Test
    fun `empty buffer is silence`() {
        assertTrue(AudioEnergy.of(FloatArray(0)).isDigitalSilence)
    }
}
