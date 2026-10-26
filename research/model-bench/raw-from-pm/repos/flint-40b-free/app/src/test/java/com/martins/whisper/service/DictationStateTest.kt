package com.martins.whisper.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationStateTest {

    @Test
    fun `idle is collapsed and not busy`() {
        assertFalse(DictationState.Idle.overlayExpanded)
        assertFalse(DictationState.Idle.isBusy)
    }

    @Test
    fun `active states expand the overlay`() {
        assertTrue(DictationState.Loading().overlayExpanded)
        assertTrue(DictationState.Recording(1000, 0.2f, true).overlayExpanded)
        assertTrue(DictationState.Processing().overlayExpanded)
        assertTrue(DictationState.Success("ok").overlayExpanded)
        assertTrue(DictationState.Error("bad").overlayExpanded)
    }

    @Test
    fun `transient result states are not busy`() {
        assertFalse(DictationState.Success("ok").isBusy)
        assertFalse(DictationState.Error("bad").isBusy)
        assertTrue(DictationState.Loading().isBusy)
        assertTrue(DictationState.Processing().isBusy)
    }

    @Test
    fun `recording exposes timer and level`() {
        val state = DictationState.Recording(elapsedMs = 12_345, level = 0.7f, speechDetected = true)
        assertEquals(12_345L, state.elapsedMs)
        assertEquals(0.7f, state.level)
        assertTrue(state.speechDetected)
    }
}
