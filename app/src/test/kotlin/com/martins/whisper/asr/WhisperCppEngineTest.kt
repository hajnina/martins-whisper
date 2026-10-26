package com.martins.whisper.asr

import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperCppEngineTest {

    @Test
    fun `host JVM reports the native library as unavailable`() {
        assertFalse(WhisperCppEngine.isNativeAvailable)
        assertNotNull(WhisperCppEngine.nativeLoadError)
        assertTrue(WhisperCppEngine.systemInfo().isNotEmpty())
    }

    @Test
    fun `create fails safely when the native library is missing`() {
        assertThrows(SpeechEngineUnavailableException::class.java) {
            WhisperCppEngine.create("/tmp/does-not-exist.bin")
        }
    }
}
