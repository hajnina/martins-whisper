package com.martins.whisper.overlay

import com.martins.whisper.service.DictationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubblePresentationTest {

    @Test
    fun `idle shows a teal mic prompt`() {
        val visual = BubblePresentation.of(DictationState.Idle)
        assertEquals(BubblePresentation.IDLE_ACCENT, visual.accent)
        assertEquals(BubbleIcon.MIC, visual.icon)
        assertEquals("Tap to dictate", visual.contentDescription)
        assertNull(visual.label)
    }

    @Test
    fun `loading shows an amber spinner with the model label`() {
        val visual = BubblePresentation.of(DictationState.Loading())
        assertEquals(BubblePresentation.LOADING_ACCENT, visual.accent)
        assertEquals(BubbleIcon.SPINNER, visual.icon)
        assertEquals("Loading model\u2026", visual.contentDescription)
        assertEquals("Loading model\u2026", visual.label)

        val custom = BubblePresentation.of(DictationState.Loading("Downloading weights"))
        assertEquals("Downloading weights", custom.contentDescription)
        assertEquals("Downloading weights", custom.label)
        assertEquals("Loading model\u2026", BubblePresentation.of(DictationState.Loading("")).contentDescription)
    }

    @Test
    fun `listening shows a red mic with level bars`() {
        val visual = BubblePresentation.of(DictationState.Recording(1_200, 0.5f, true))
        assertEquals(BubblePresentation.LISTENING_ACCENT, visual.accent)
        assertEquals(BubbleIcon.MIC_LEVELS, visual.icon)
        assertEquals("Listening; tap to stop", visual.contentDescription)
    }

    @Test
    fun `transcribing shows a distinct blue three dot icon`() {
        val visual = BubblePresentation.of(DictationState.Processing())
        assertEquals(BubbleIcon.SPINNER, BubblePresentation.of(DictationState.Loading()).icon)
        assertEquals(BubblePresentation.TRANSCRIBING_ACCENT, visual.accent)
        assertEquals(BubbleIcon.TRANSCRIBING, visual.icon)
        assertEquals("Transcribing\u2026", visual.contentDescription)
        assertEquals("Transcribing\u2026", visual.label)
        assertNotEquals(BubbleIcon.SPINNER, visual.icon)
        assertNotEquals(BubblePresentation.LOADING_ACCENT, visual.accent)
    }

    @Test
    fun `success shows a green check carrying the message`() {
        val visual = BubblePresentation.of(DictationState.Success("Inserted 12 words"))
        assertEquals(BubblePresentation.SUCCESS_ACCENT, visual.accent)
        assertEquals(BubbleIcon.CHECK, visual.icon)
        assertTrue(visual.contentDescription.contains("Inserted 12 words"))
        assertEquals("Inserted 12 words", visual.label)
    }

    @Test
    fun `error shows a dark red cross carrying the message`() {
        val visual = BubblePresentation.of(DictationState.Error("Microphone unavailable"))
        assertEquals(BubblePresentation.ERROR_ACCENT, visual.accent)
        assertEquals(BubbleIcon.CROSS, visual.icon)
        assertTrue(visual.contentDescription.contains("Microphone unavailable"))
        assertTrue(visual.contentDescription.startsWith("Error:"))
        assertEquals("Microphone unavailable", visual.label)
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
