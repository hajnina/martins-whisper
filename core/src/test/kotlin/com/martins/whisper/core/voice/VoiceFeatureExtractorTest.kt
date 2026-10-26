package com.martins.whisper.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class VoiceFeatureExtractorTest {

    private val sampleRate = 16_000
    private val size = 1024

    private fun sine(frequencyHz: Double, amplitude: Float, samples: Int = size): FloatArray =
        FloatArray(samples) { i ->
            (amplitude * sin(2.0 * PI * frequencyHz * i / sampleRate)).toFloat()
        }

    private fun silence(samples: Int = size): FloatArray = FloatArray(samples)

    @Test
    fun `220 Hz sine is voiced at 220 Hz`() {
        val features = VoiceFeatureExtractor(sampleRate).process(sine(220.0, 0.4f), timestampMs = 1_000L)

        assertNotNull(features.pitchHz)
        assertEquals(220.0, features.pitchHz!!.toDouble(), 3.0)
        assertTrue("voicedProb=${features.voicedProb}", features.voicedProb > 0.8f)
        assertTrue("level=${features.level}", features.level > 0.3f)
        assertTrue(features.pitchNorm in 0f..1f)
        assertEquals(1_000L, features.timestampMs)
    }

    @Test
    fun `white noise is unvoiced`() {
        val random = Random(7)
        val noise = FloatArray(size) { (random.nextFloat() * 2f - 1f) * 0.3f }
        val features = VoiceFeatureExtractor(sampleRate).process(noise)

        assertNull("white noise must not report a pitch", features.pitchHz)
        assertEquals(0f, features.voicedProb, 0f)
        assertEquals(0f, features.pitchNorm, 0f)
        assertTrue("noise still has real level", features.level > 0.2f)
    }

    @Test
    fun `silence has zero level and no pitch`() {
        val extractor = VoiceFeatureExtractor(sampleRate)
        var features = VoiceFeatures.SILENT
        repeat(4) { features = extractor.process(silence()) }

        assertEquals(0f, features.level, 1e-6f)
        assertEquals(0f, features.peakLevel, 1e-6f)
        assertNull(features.pitchHz)
        assertFalse(features.onset)
    }

    @Test
    fun `level rises monotonically with amplitude`() {
        val amplitudes = floatArrayOf(0.05f, 0.1f, 0.2f, 0.4f)
        val levels = amplitudes.map { amplitude ->
            val extractor = VoiceFeatureExtractor(sampleRate)
            var features = extractor.process(sine(220.0, amplitude))
            repeat(5) { features = extractor.process(sine(220.0, amplitude)) }
            features.level
        }
        for (i in 1 until levels.size) assertTrue("levels=$levels", levels[i] > levels[i - 1])
        assertTrue(levels.first() < levels.last())
    }

    @Test
    fun `pitch normalised position rises across a 200 to 400 Hz glide`() {
        val extractor = VoiceFeatureExtractor(sampleRate)
        val frames = 24
        var first = 0f
        var last = 0f
        for (i in 0 until frames) {
            val hz = 200.0 + 200.0 * i / (frames - 1)
            val features = extractor.process(sine(hz, 0.4f))
            if (i == 0) first = features.pitchNorm
            last = features.pitchNorm
        }
        assertTrue("pitchNorm should rise: first=$first last=$last", last > first + 0.15f)
        assertTrue(last in 0f..1f)
    }

    @Test
    fun `clicks produce an onset, silence does not`() {
        val extractor = VoiceFeatureExtractor(sampleRate)
        repeat(3) { assertFalse(extractor.process(silence()).onset) }
        val click = FloatArray(size).also { it[512] = 1f }
        assertTrue("a full-scale click after silence is an onset", extractor.process(click).onset)
    }
}
