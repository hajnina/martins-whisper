package com.martins.whisper.overlay

import com.martins.whisper.service.DictationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubblePresentationTest {

    @Test
    fun `idle shows the indigo mic prompt`() {
        val visual = BubblePresentation.of(DictationState.Idle)
        assertEquals(BubblePresentation.IDLE_ACCENT, visual.accent)
        assertEquals(0xFF8C89FF.toInt(), visual.accent)
        assertEquals(BubbleIcon.MIC, visual.icon)
        assertEquals("Tap to dictate", visual.contentDescription)
        assertNull(visual.label)
    }

    @Test
    fun `loading shows a lavender spinner with the model label`() {
        val visual = BubblePresentation.of(DictationState.Loading())
        assertEquals(BubblePresentation.LOADING_ACCENT, visual.accent)
        assertEquals(0xFFD8D7FF.toInt(), visual.accent)
        assertEquals(BubbleIcon.SPINNER, visual.icon)
        assertEquals("Loading model\u2026", visual.contentDescription)
        assertEquals("Loading model\u2026", visual.label)

        val custom = BubblePresentation.of(DictationState.Loading("Downloading weights"))
        assertEquals("Downloading weights", custom.contentDescription)
        assertEquals("Downloading weights", custom.label)
        assertEquals("Loading model\u2026", BubblePresentation.of(DictationState.Loading("")).contentDescription)
    }

    @Test
    fun `recording shows the teal mic with level bars`() {
        val visual = BubblePresentation.of(DictationState.Recording(1_200, 0.5f, true))
        assertEquals(BubblePresentation.RECORDING_ACCENT, visual.accent)
        assertEquals(0xFF5EEAD4.toInt(), visual.accent)
        assertEquals(BubbleIcon.MIC_LEVELS, visual.icon)
        assertEquals("Listening; tap to stop", visual.contentDescription)
    }

    @Test
    fun `processing shows a periwinkle three dot icon`() {
        val visual = BubblePresentation.of(DictationState.Processing())
        assertEquals(BubbleIcon.SPINNER, BubblePresentation.of(DictationState.Loading()).icon)
        assertEquals(BubblePresentation.PROCESSING_ACCENT, visual.accent)
        assertEquals(0xFFB3B4FF.toInt(), visual.accent)
        assertEquals(BubbleIcon.TRANSCRIBING, visual.icon)
        assertEquals("Transcribing\u2026", visual.contentDescription)
        assertEquals("Transcribing\u2026", visual.label)
        assertNotEquals(BubbleIcon.SPINNER, visual.icon)
        assertNotEquals(BubblePresentation.LOADING_ACCENT, visual.accent)
    }

    @Test
    fun `success shows a deep teal check carrying the message`() {
        val visual = BubblePresentation.of(DictationState.Success("Inserted 12 words"))
        assertEquals(BubblePresentation.SUCCESS_ACCENT, visual.accent)
        assertEquals(0xFF008A80.toInt(), visual.accent)
        assertEquals(BubbleIcon.CHECK, visual.icon)
        assertTrue(visual.contentDescription.contains("Inserted 12 words"))
        assertEquals("Inserted 12 words", visual.label)
    }

    @Test
    fun `error shows the scheme error cross carrying the message`() {
        val visual = BubblePresentation.of(DictationState.Error("Microphone unavailable"))
        assertEquals(BubblePresentation.ERROR_ACCENT, visual.accent)
        assertEquals(0xFFFFB4AB.toInt(), visual.accent)
        assertEquals(BubbleIcon.CROSS, visual.icon)
        assertTrue(visual.contentDescription.contains("Microphone unavailable"))
        assertTrue(visual.contentDescription.startsWith("Error:"))
        assertEquals("Microphone unavailable", visual.label)
    }

    @Test
    fun `accents come from the shared overlay palette`() {
        assertEquals(WhisperOverlayPalette.IdleAccent, BubblePresentation.IDLE_ACCENT)
        assertEquals(WhisperOverlayPalette.LoadingAccent, BubblePresentation.LOADING_ACCENT)
        assertEquals(WhisperOverlayPalette.RecordingAccent, BubblePresentation.RECORDING_ACCENT)
        assertEquals(WhisperOverlayPalette.ProcessingAccent, BubblePresentation.PROCESSING_ACCENT)
        assertEquals(WhisperOverlayPalette.SuccessAccent, BubblePresentation.SUCCESS_ACCENT)
        assertEquals(WhisperOverlayPalette.ErrorAccent, BubblePresentation.ERROR_ACCENT)
    }

    @Test
    fun `all six states are visually and accessibly distinct`() {
        val states = listOf(
            DictationState.Idle,
            DictationState.Loading(),
            DictationState.Recording(0, 0f, false),
            DictationState.Processing(),
            DictationState.Success("Inserted"),
            DictationState.Error("nope"),
        )
        val visuals = states.map(BubblePresentation::of)

        assertEquals(6, visuals.map { it.accent }.toSet().size)
        assertEquals(6, visuals.map { it.icon }.toSet().size)
        assertEquals(6, visuals.map { it.contentDescription }.toSet().size)
        assertTrue(visuals.all { it.contentDescription.isNotBlank() })
    }
}
