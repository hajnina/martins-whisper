package com.martins.whisper.core

import com.martins.whisper.core.audio.EnergyVad
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnergyVadTest {
    private val silence = ShortArray(320)
    private val speech = ShortArray(320) { 2_000 }

    @Test
    fun `silence is not speech`() {
        val vad = EnergyVad()
        assertFalse(vad.isSpeech(silence))
        assertFalse(vad.isSpeech(silence))
    }

    @Test
    fun `tone is detected immediately`() {
        val vad = EnergyVad()
        assertTrue(vad.isSpeech(speech))
    }

    @Test
    fun `hangover keeps speech active for configured frames`() {
        val vad = EnergyVad(hangoverFrames = 8)
        assertTrue(vad.isSpeech(speech))
        repeat(8) { assertTrue(vad.isSpeech(silence)) }
        assertFalse(vad.isSpeech(silence))
    }

    @Test
    fun `reset clears hangover and floor`() {
        val vad = EnergyVad()
        assertTrue(vad.isSpeech(speech))
        vad.reset()
        assertFalse(vad.isSpeech(silence))
    }

    @Test
    fun `minimum speech level rejects quiet tone`() {
        val vad = EnergyVad(minSpeechRmsDb = -30.0)
        val quiet = ShortArray(320) { 500 }
        assertFalse(vad.isSpeech(quiet))
    }

    @Test
    fun `adaptive floor eventually rejects constant background`() {
        val vad = EnergyVad()
        val background = ShortArray(320) { 100 }
        repeat(2_000) { vad.isSpeech(background) }
        assertFalse(vad.isSpeech(background))
        assertTrue(vad.isSpeech(ShortArray(320) { 8_000 }))
    }
}
