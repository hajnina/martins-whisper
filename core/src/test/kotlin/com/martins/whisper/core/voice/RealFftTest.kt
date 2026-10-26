package com.martins.whisper.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class RealFftTest {

    private val size = 1024
    private val sampleRate = 16_000
    private val window = FloatArray(size) { 1f }

    @Test
    fun `a pure tone peaks in the matching bin`() {
        val frequency = 1000.0
        val samples = FloatArray(size) { i ->
            sin(2.0 * PI * frequency * i / sampleRate).toFloat()
        }
        val out = FloatArray(size / 2)
        RealFft(size).magnitude(samples, window, out)

        val expectedBin = (frequency * size / sampleRate).toInt()
        var best = 1
        for (bin in 2 until size / 2) if (out[bin] > out[best]) best = bin
        assertEquals(expectedBin, best)
        assertEquals(0f, out[0], 0f)
    }

    @Test
    fun `silence has zero magnitude and an impulse is broadband`() {
        val silent = FloatArray(size / 2)
        RealFft(size).magnitude(FloatArray(size), window, silent)
        assertTrue(silent.all { it == 0f })

        val impulse = FloatArray(size).also { it[512] = 1f }
        val spectrum = FloatArray(size / 2)
        RealFft(size).magnitude(impulse, window, spectrum)
        assertTrue(spectrum.drop(1).all { it > 0.9f })
    }
}
