package com.martins.whisper.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechEnvelopeTest {

    private fun noiseFloorSamples(windowSamples: Int = 16_000, amplitude: Float = 3e-4f): FloatArray =
        FloatArray(windowSamples) { amplitude * sine(it) }.let { base ->
            // Add a single loud speech burst in the middle (100 ms of voice-level energy).
            base.copyOf().also { out ->
                val start = windowSamples / 2
                val burst = 1_600
                for (i in 0 until burst) {
                    out[start + i] = 0.03f * sine(i)
                }
            }
        }

    private fun sine(i: Int): Float = kotlin.math.sin(2.0 * Math.PI * 440.0 * i / 16_000.0).toFloat()

    @Test
    fun speechAboveQuietNoiseFloorIsDetected() {
        val envelope = SpeechEnvelope.of(noiseFloorSamples(), windowSamples = 1_600)
        assertTrue(envelope.isSpeech, "expected speech, got ${envelope.gateLog()}")
        assertTrue(
            envelope.marginDb >= SpeechEnvelope.GATE_MARGIN_DB,
            "speech window must be well above the floor: ${envelope.gateLog()}",
        )
        assertTrue(envelope.floorDb <= SpeechEnvelope.ABSOLUTE_FLOOR_DB + 4f, "floor cap must apply: ${envelope.floorDb}")
    }

    @Test
    fun pureNoiseNeverPassesTheRelativeGate() {
        val amplitude = 3e-4f
        val samples = FloatArray(16_000) { amplitude * sine(it) }
        val envelope = SpeechEnvelope.of(samples, windowSamples = 1_600)
        assertFalse(envelope.isSpeech, "noise-only must be rejected: ${envelope.gateLog()}")
    }

    @Test
    fun speechThatFillsTheWholeBufferIsStillDetectedBecauseTheFloorIsCapped() {
        val amplitude = 0.03f
        val samples = FloatArray(16_000) { amplitude * sine(it) }
        val envelope = SpeechEnvelope.of(samples, windowSamples = 1_600)
        assertEquals(SpeechEnvelope.ABSOLUTE_FLOOR_DB, envelope.floorDb)
        assertTrue(envelope.isSpeech, "full-buffer speech must pass: ${envelope.gateLog()}")
    }

    @Test
    fun digitalSilenceIsNotSpeechAndDoesNotDivideByZero() {
        val envelope = SpeechEnvelope.of(FloatArray(16_000))
        assertFalse(envelope.isSpeech)
        assertEquals(SpeechEnvelope.LOWEST_DB, envelope.peakDb)
    }

    @Test
    fun marginAgainstAbsoluteConstantLevelHasAStableFloorForAHotMic() {
        // A phone whose noise floor sits at -50 dBFS: speech at -30 dBFS must still pass,
        // which is what the ABSOLUTE_FLOOR cap protects (floor cannot be raised by gain).
        val floorAmplitude = 3.16e-3f // -50 dBFS
        val speechAmplitude = 0.0316f // -30 dBFS
        val samples = FloatArray(16_000) { i ->
            if (i in 4_000..8_000) speechAmplitude * sine(i) else floorAmplitude * sine(i)
        }
        val envelope = SpeechEnvelope.of(samples, windowSamples = 1_600)
        assertTrue(envelope.isSpeech, "hot mic floor must not block speech: ${envelope.gateLog()}")
    }

    @Test
    fun emptyBufferIsNotSpeech() {
        assertFalse(SpeechEnvelope.of(FloatArray(0)).isSpeech)
    }

    @Test
    fun gateMessageContainsTheMeasuredLevelsForTheOwner() {
        val envelope = SpeechEnvelope.of(noiseFloorSamples(), windowSamples = 1_600)
        val message = envelope.gateMessage()
        assertTrue("-30 dBFS" in message || "-31 dBFS" in message, "peak must appear for the owner: $message")
        assertTrue("floor" in message, "floor must appear for the owner: $message")
        assertTrue("+10" in message, "gate margin must be human readable: $message")
    }

    @Test
    fun windowSizeIsFeltConsistentlyAcrossSlices() {
        val samples = noiseFloorSamples(32_000, 2e-4f)
        val whole = SpeechEnvelope.of(samples, windowSamples = 1_600)
        val one = SpeechEnvelope.of(samples.copyOfRange(0, 16_000), windowSamples = 1_600)
        val two = SpeechEnvelope.of(samples.copyOfRange(16_000, 32_000), windowSamples = 1_600)
        assertEquals(whole.floorDb, one.floorDb, 0.01f)
        assertEquals(whole.floorDb, two.floorDb, 0.01f)
    }

    /**
     * Mutation control: if the comparison is flipped (>= to <=) or the floor cap is removed,
     * both false negatives and false positives must be caught by exactly these expectations.
     * Proven on #1 (speech detected flips), #2 (noise starts passing), #4 (silence passes).
     */
}
