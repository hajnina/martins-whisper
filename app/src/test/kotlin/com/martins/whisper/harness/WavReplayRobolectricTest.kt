package com.martins.whisper.harness

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Looper
import com.martins.whisper.asr.WhisperCppEngine
import com.martins.whisper.audio.AudioSource
import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.Transcript
import com.martins.whisper.core.TranscriptGate
import com.martins.whisper.core.asr.FakeSpeechEngine
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.service.DictationService
import com.martins.whisper.service.DictationState
import com.martins.whisper.service.EngineFactory
import com.martins.whisper.service.InsertResult
import com.martins.whisper.service.TextInserter
import java.io.File
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

/**
 * Host replay: a WAV file goes through the same production path as a real dictation
 * (DictationService -> AudioCaptureBuffer -> DictationEngineHost -> engine -> TranscriptGate),
 * not a reimplementation. The public jfk.wav case uses the real whisper.cpp JNI engine; the
 * synthetic sweep uses the fake engine to isolate where the speech gate flips.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class WavReplayRobolectricTest {

    private lateinit var app: Application
    private lateinit var originalEngineFactory: (Context, SettingsStore) -> SpeechEngine
    private lateinit var originalInserterFactory: (Context) -> TextInserter
    private lateinit var originalAudioSourceFactory: (Context) -> AudioSource

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        originalEngineFactory = EngineFactory.factory
        originalInserterFactory = DictationService.inserterFactory
        originalAudioSourceFactory = DictationService.audioSourceFactory
    }

    @After
    fun tearDown() {
        EngineFactory.factory = originalEngineFactory
        DictationService.inserterFactory = originalInserterFactory
        DictationService.audioSourceFactory = originalAudioSourceFactory
    }

    @Test
    fun replayWavAssertsExpectedWordsAndGateVerdict() {
        val wav = locateWav()
        val model = locateModel()
        assumeTrue(
            "set WHISPER_REPLAY_WAV and WHISPER_REPLAY_MODEL (or run scripts/replay-wav.sh)",
            wav != null && model != null,
        )
        assumeTrue(
            "host libmartins_whisper.so is not loadable: ${WhisperCppEngine.nativeLoadError}",
            WhisperCppEngine.isNativeAvailable,
        )

        val audio = WavFixtures.read(wav!!)
        SettingsStore(app).language = "en"
        val outcome = replay(audio.samples) {
            WhisperCppEngine.create(model!!.absolutePath, ComputeBackend.CPU, threads = 4)
        }

        val transcript = outcome.inserted.orEmpty()
        val gate = if (outcome.inserted != null) "SPEAK" else "REJECT"
        report(
            buildList {
                add("wav=${wav.absolutePath}")
                add("samples=${audio.samples.size}")
                add("duration_ms=${audio.samples.size * 1_000L / audio.sampleRate}")
                add("peak_dbfs=${format(WavFixtures.peakDbfs(audio.samples))}")
                add("rms_dbfs=${format(WavFixtures.rmsDbfs(audio.samples))}")
                add("gate=$gate")
                add("transcript=$transcript")
                if (outcome.inserted == null) add("reason=${(outcome.state as? DictationState.Error)?.message ?: outcome.state}")
            },
        )

        val expected = expectedWords(wav)
        val inserted = outcome.inserted
        if (inserted == null) {
            val error = outcome.state as? DictationState.Error
            assertNotNull("replay failed without a gate verdict: ${outcome.state}", error)
            assertTrue(
                "unexpected failure instead of a gate verdict: ${error!!.message}",
                error.message == TranscriptGate.EMPTY_MESSAGE ||
                    error.message == TranscriptGate.SILENT_MIC_MESSAGE,
            )
        } else {
            assertTrue("inserted text must end in Success, got ${outcome.state}", outcome.state is DictationState.Success)
        }
        if (expected.isNotEmpty()) {
            assertNotNull("gate rejected audio but expected speech: ${outcome.state}", inserted)
            val normalized = inserted!!.lowercase()
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            expected.forEach { phrase ->
                val wanted = phrase.lowercase()
                    .replace(Regex("[^a-z0-9 ]"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                assertTrue("transcript missing '$phrase', got: '$inserted'", normalized.contains(wanted))
            }
            assertEquals("gate verdict", "SPEAK", gate)
        }
    }

    @Test
    fun syntheticQuietSpeechShowsWhereGateFlips() {
        val levels = listOf(-60.0, -50.0, -45.0, -40.0, -39.0, -38.0, -37.5, -37.0, -36.5, -36.0, -35.0, -30.0, -20.0)
        val results = levels.map { db ->
            val samples = WavFixtures.speechLike(seconds = 1.0, rmsDbfs = db)
            val outcome = replay(samples) {
                FakeSpeechEngine(
                    backend = "replay-fake",
                    respond = { _, language -> Transcript(text = "hello world", language = language ?: "en") },
                )
            }
            val spoken = outcome.inserted != null
            LevelResult(
                dbfs = db,
                measuredRmsDbfs = WavFixtures.rmsDbfs(samples),
                spoken = spoken,
                reason = if (spoken) null else (outcome.state as? DictationState.Error)?.message,
                transcript = outcome.inserted.orEmpty(),
            )
        }

        report(
            buildList {
                add("synthetic speech-like levels (steady harmonic 120 Hz complex, 1 s each)")
                results.forEach { result ->
                    val gate = if (result.spoken) "SPEAK" else "REJECT"
                    add(
                        "level_dbfs=${format(result.dbfs)} measured_rms_dbfs=${format(result.measuredRmsDbfs)} " +
                            "gate=$gate" +
                            (result.reason?.let { " reason=$it" } ?: "") +
                            (result.transcript.takeIf { it.isNotEmpty() }?.let { " transcript=$it" } ?: ""),
                    )
                }
                val firstSpeak = results.firstOrNull { it.spoken }
                val lastReject = results.lastOrNull { !it.spoken }
                add("gate_flip=between ${lastReject?.dbfs?.let(::format) ?: "n/a"} and ${firstSpeak?.dbfs?.let(::format) ?: "n/a"} dBFS")
                add("declared_speech_threshold_dbfs=${format(WavFixtures.linearToDbfs(SPEECH_RMS_THRESHOLD))}")
            },
        )

        // Relative gate: level alone is not speech below the uniformly-loud rule.
        // Below the -45 dBFS uniform-loud cap, a steady tone has no margin above its own
        // floor and must be rejected with the measured-level message.
        val rejects = results.filter { !it.spoken }
        assertEquals("noise at or under -45 dBFS must reject", levels.count { it <= -45.0 }, rejects.size)
        assertTrue(
            "rejections must carry the gate message with measurements: $rejects",
            rejects.all { it.reason?.startsWith(TranscriptGate.EMPTY_MESSAGE) == true },
        )
        val speaks = results.filter { it.spoken }
        assertTrue(
            "uniformly loud levels must still speak: $speaks",
            speaks.all { it.dbfs >= -45.0 && it.transcript == "hello world" },
        )

        // The gate exists for THIS: quiet-but-dynamic speech (floor -60 dBFS, bursts -30 dBFS)
        // must speak. The old absolute per-frame gate (-36.5 dBFS) rejected exactly this.
        val dynamicSamples = WavFixtures.bursty(seconds = 2.0, floorDbfs = -60.0, burstDbfs = -30.0)
        val dynamic = replay(dynamicSamples) {
            FakeSpeechEngine(
                backend = "replay-fake",
                respond = { _, language -> Transcript(text = "hello world", language = language ?: "en") },
            )
        }
        assertTrue(
            "quiet bursts above the floor must be detected: peak ${format(WavFixtures.peakDbfs(dynamicSamples))} dBFS",
            dynamic.inserted == "hello world",
        )
    }

    private fun replay(samples: ShortArray, engineFactory: () -> SpeechEngine): Outcome {
        val inserted = AtomicReference<String?>(null)
        EngineFactory.factory = { _, _ -> engineFactory() }
        DictationService.inserterFactory = {
            object : TextInserter {
                override fun insert(text: String): InsertResult {
                    inserted.set(text)
                    return InsertResult.Inserted
                }
            }
        }
        DictationService.audioSourceFactory = { WavAudioSource(WavFixtures.frames(samples)) }

        val controller = Robolectric.buildService(DictationService::class.java).create()
        val service = controller.get()
        return try {
            invoke(service, "onPress")
            await("engine ready and recording") { DictationService.state.value is DictationState.Recording }
            ShadowSystemClock.advanceBy(Duration.ofMillis(HOLD_MS))
            invoke(service, "onRelease")
            await("gate verdict") {
                DictationService.state.value is DictationState.Success ||
                    DictationService.state.value is DictationState.Error
            }
            Outcome(inserted.get(), DictationService.state.value)
        } finally {
            controller.destroy()
        }
    }

    private fun invoke(service: DictationService, method: String) {
        DictationService::class.java
            .getDeclaredMethod(method)
            .apply { isAccessible = true }
            .invoke(service)
    }

    private fun await(what: String, timeoutMs: Long = 60_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) {
                shadowOf(Looper.getMainLooper()).idle()
                return
            }
            Thread.sleep(10)
        }
        throw AssertionError("timed out waiting for $what; state=${DictationService.state.value}")
    }

    private fun locateWav(): File? {
        val candidates = listOfNotNull(
            System.getenv("WHISPER_REPLAY_WAV"),
            System.getenv("WHISPER_HOST_JFK"),
            "app/src/main/cpp/whisper.cpp/samples/jfk.wav",
            "src/main/cpp/whisper.cpp/samples/jfk.wav",
            "../app/src/main/cpp/whisper.cpp/samples/jfk.wav",
            "/work/whisper.cpp/samples/jfk.wav",
            "build/host-native/samples/jfk.wav",
        )
        return candidates.map(::File).firstOrNull { it.isFile }
    }

    private fun locateModel(): File? {
        val candidates = listOfNotNull(
            System.getenv("WHISPER_REPLAY_MODEL"),
            System.getenv("WHISPER_HOST_MODEL"),
            "build/host-native/models/ggml-tiny.bin",
            "../build/host-native/models/ggml-tiny.bin",
            "/work/e1-models/ggml-tiny.bin",
        )
        return candidates.map(::File).firstOrNull { it.isFile }
    }

    private fun report(lines: List<String>) {
        val text = lines.joinToString("\n")
        println(text)
        System.getenv("WHISPER_REPLAY_OUT")?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).apply { parentFile?.mkdirs() }.writeText(text + "\n")
        }
    }

    private fun expectedWords(wav: File): List<String> {
        val configured = System.getenv("WHISPER_REPLAY_EXPECT").orEmpty()
        if (configured.isNotBlank()) return configured.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        return if (wav.name.equals("jfk.wav", ignoreCase = true)) {
            listOf("ask not what your country can do for you", "fellow americans")
        } else {
            emptyList()
        }
    }

    private fun declaredSpeechThreshold(): Float =
        DictationService::class.java
            .getDeclaredField("SPEECH_RMS_THRESHOLD")
            .apply { isAccessible = true }
            .getFloat(null)

    private fun format(value: Double): String = String.format("%.2f", value)

    private data class Outcome(val inserted: String?, val state: DictationState)

    private data class LevelResult(
        val dbfs: Double,
        val measuredRmsDbfs: Double,
        val spoken: Boolean,
        val reason: String?,
        val transcript: String,
    )

    private class WavAudioSource(private val frames: List<ShortArray>) : AudioSource {
        override fun start(onFrame: (ShortArray, Long) -> Unit, onError: (Throwable) -> Unit) {
            var time = 1_000L
            for (frame in frames) {
                onFrame(frame, time)
                time += frame.size * 1_000L / WavFixtures.SAMPLE_RATE
            }
        }

        override fun stop() = Unit
        override fun release() = Unit
    }

    private companion object {
        const val HOLD_MS = 700L
        const val SPEECH_RMS_THRESHOLD = 0.015
    }
}
