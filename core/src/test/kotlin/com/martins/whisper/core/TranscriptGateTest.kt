package com.martins.whisper.core

import com.martins.whisper.core.TranscriptGate.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptGateTest {

    @Test
    fun `silence hallucinations are rejected literally`() {
        listOf(
            "Thank you.",
            "Thanks for watching!",
            "Thank you for watching.",
            "Thanks!",
            "Please subscribe.",
            "you",
        ).forEach { raw ->
            val decision = TranscriptGate.evaluate(raw, speechDetected = false, peakLevel = 0.2f)
            assertTrue("expected reject for '$raw'", decision is Decision.Reject)
        }
    }

    @Test
    fun `hallucinations are also rejected when energy was detected but the strip leaves nothing`() {
        listOf(
            "[BLANK_AUDIO]",
            "[ Silence ]",
            "(upbeat music)",
            "[BLANK_AUDIO] Thank you.",
            "<noise> [music]",
        ).forEach { raw ->
            val decision = TranscriptGate.evaluate(raw, speechDetected = true, peakLevel = 0.2f)
            assertTrue("expected reject for '$raw'", decision is Decision.Reject)
            assertEquals(TranscriptGate.EMPTY_MESSAGE, (decision as Decision.Reject).message)
        }
    }

    @Test
    fun `bracketed tokens are stripped from real speech`() {
        assertEquals(
            "Hello world",
            TranscriptGate.clean("[BLANK_AUDIO] Hello [noise] world [music]"),
        )
        assertEquals("Ahoj svete", TranscriptGate.clean("(music) Ahoj   svete <noise>"))
    }

    @Test
    fun `a real phrase is accepted and trimmed`() {
        val decision = TranscriptGate.evaluate("  Hello there.  ", speechDetected = true, peakLevel = 0.2f)
        assertEquals(Decision.Speak("Hello there."), decision)
    }

    @Test
    fun `a phrase that merely contains a hallucination phrase is kept`() {
        listOf(
            "Thank you for the update.",
            "You are welcome.",
            "Thanks, I will call you tomorrow.",
        ).forEach { raw ->
            assertEquals(Decision.Speak(raw), TranscriptGate.evaluate(raw, true, 0.2f))
        }
    }

    @Test
    fun `no speech energy rejects even a plausible transcript`() {
        val decision = TranscriptGate.evaluate("Hello there", speechDetected = false, peakLevel = 0.3f)
        assertEquals(Decision.Reject("no speech energy"), decision)
    }

    @Test
    fun `digital silence is a distinct microphone error`() {
        val decision = TranscriptGate.evaluate("Thank you.", speechDetected = false, peakLevel = 0f)
        assertEquals(
            Decision.Reject("silent microphone", TranscriptGate.SILENT_MIC_MESSAGE),
            decision,
        )
    }

    @Test
    fun `empty raw transcript says did not catch that`() {
        assertEquals(
            Decision.Reject("empty transcript", "Didn't catch that"),
            TranscriptGate.evaluate("   ", true, 0.2f),
        )
    }
}
