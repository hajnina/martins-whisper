package com.martins.whisper.harness

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.martins.whisper.asr.WhisperCppEngine
import com.martins.whisper.core.AudioChunk
import com.martins.whisper.core.ChunkReason
import com.martins.whisper.core.ComputeBackend
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layer 2 (device-only) smoke test for the real whisper.cpp JNI build.
 *
 * Build the APK with `-Pmartins.native=true`, then either download a catalog model in the app or
 * push one before installing:
 *
 *   adb push ggml-tiny.bin /data/local/tmp/ggml-tiny.bin
 *   adb shell run-as com.martins.whisper mkdir -p files/models
 *   adb shell run-as com.martins.whisper cp /data/local/tmp/ggml-tiny.bin files/models/tiny.bin
 *
 * Alternatively pass an explicit path:
 *
 *   adb shell am instrument -w \
 *     -e whisperModel /data/local/tmp/ggml-tiny.bin \
 *     -e class com.martins.whisper.harness.NativeWhisperSmokeTest \
 *     com.martins.whisper.test/androidx.test.runner.AndroidJUnitRunner
 *
 * The model is never bundled in the APK; without one the transcribe test is skipped.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class NativeWhisperSmokeTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun nativeLibraryReportsVersionAndBackends() {
        assumeTrue(
            "APK has no native whisper.cpp (build with -Pmartins.native=true): " +
                WhisperCppEngine.nativeLoadError,
            WhisperCppEngine.isNativeAvailable,
        )
        assertTrue(WhisperCppEngine.systemInfo().isNotBlank())
        assertTrue(WhisperCppEngine.backendDevices().isNotBlank())
    }

    @Test
    fun tinyModelLoadsAndTranscribesSilence() = runBlocking {
        assumeTrue("APK has no native whisper.cpp", WhisperCppEngine.isNativeAvailable)
        val model = modelFile()
        assumeTrue(
            "No GGML model installed; push one to files/models or pass -e whisperModel <path>",
            model != null,
        )

        val engine = WhisperCppEngine.create(model!!.absolutePath, ComputeBackend.CPU)
        try {
            val transcript = engine.transcribe(
                AudioChunk(
                    index = 0,
                    samples = FloatArray(16_000),
                    sampleRate = 16_000,
                    startMs = 0,
                    endMs = 1_000,
                    reason = ChunkReason.FLUSH,
                ),
                language = "en",
            )
            assertNotNull(transcript)
        } finally {
            engine.close()
        }
    }

    private fun modelFile(): File? {
        val explicit = InstrumentationRegistry.getArguments().getString("whisperModel")
        if (!explicit.isNullOrBlank()) {
            return File(explicit).takeIf { it.isFile && it.length() > 0 }
        }
        val models = File(context.filesDir, "models")
        return models.listFiles { file -> file.isFile && file.length() > 0 }
            ?.filter { it.extension == "bin" }
            ?.sortedBy { it.length() }
            ?.firstOrNull()
    }
}
