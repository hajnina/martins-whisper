package com.martins.whisper.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServiceContractTest {

    @Test
    fun `notification actions are distinct`() {
        val actions = listOf(
            DictationService.ACTION_START,
            DictationService.ACTION_STOP,
            DictationService.ACTION_SETTINGS,
            DictationService.ACTION_TRANSCRIPT,
        )
        assertEquals(actions.size, actions.toSet().size)
        assertNotEquals(DictationService.ACTION_START, DictationService.ACTION_STOP)
    }

    @Test
    fun `action namespace is stable for I1 integration`() {
        assertEquals("com.martins.whisper.action.TRANSCRIPT", DictationService.ACTION_TRANSCRIPT)
        assertEquals("transcript", DictationService.EXTRA_TRANSCRIPT)
        assertEquals("dictation", DictationService.CHANNEL_ID)
    }
}
