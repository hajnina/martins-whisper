package com.martins.whisper.audio

import android.media.MediaRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRecordSourceTest {

    @Test
    fun `mic is preferred over voice recognition by default`() {
        assertEquals(
            listOf(
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
            ),
            AudioRecordSource.sourceCandidates(preferred = null, supportsUnprocessed = false),
        )
    }

    @Test
    fun `unprocessed is tried first when the device advertises it`() {
        assertEquals(
            listOf(
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
            ),
            AudioRecordSource.sourceCandidates(preferred = null, supportsUnprocessed = true),
        )
    }

    @Test
    fun `an explicit preferred source disables the fallback chain`() {
        assertEquals(
            listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION),
            AudioRecordSource.sourceCandidates(
                preferred = MediaRecorder.AudioSource.VOICE_RECOGNITION,
                supportsUnprocessed = true,
            ),
        )
    }

    @Test
    fun `sources have stable diagnostic names`() {
        assertEquals("MIC", AudioRecordSource.sourceNameFor(MediaRecorder.AudioSource.MIC))
        assertEquals(
            "VOICE_RECOGNITION",
            AudioRecordSource.sourceNameFor(MediaRecorder.AudioSource.VOICE_RECOGNITION),
        )
        assertEquals(
            "UNPROCESSED",
            AudioRecordSource.sourceNameFor(MediaRecorder.AudioSource.UNPROCESSED),
        )
        assertTrue(AudioRecordSource.sourceNameFor(9999).startsWith("source#"))
    }
}
