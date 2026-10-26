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
    fun jfkReplayAssertsLiteralWordsAndSpeakVerdict() {
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
                if (outcome.inserted == null) add("reason=${outcome.state}")
            },
        )

        assertNotNull("gate rejected jfk: ${outcome.state}", outcome.inserted)
        val normalized = transcript.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        assertTrue("transcript missing the JFK line, got: '$transcript'", normalized.contains("ask not what your country can do for you"))
        assertTrue("transcript missing 'fellow americans', got: '$transcript'", normalized.contains("fellow americans"))
        assertEquals("gate verdict", "SPEAK", gate)
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

        assertEquals(
            "harness mirrors DictationService.SPEECH_RMS_THRESHOLD",
            declaredSpeechThreshold().toDouble(),
            SPEECH_RMS_THRESHOLD,
            1e-9,
        )
        val thresholdDb = WavFixtures.linearToDbfs(SPEECH_RMS_THRESHOLD)
        val rejects = results.filter { !it.spoken }
        val speaks = results.filter { it.spoken }
        assertTrue("no level was rejected below the gate", rejects.isNotEmpty())
        assertTrue("no level passed above the gate", speaks.isNotEmpty())
        assertTrue(
            "quiet levels must reject with the no-speech gate: $rejects",
            rejects.all { it.dbfs < thresholdDb && it.reason == TranscriptGate.EMPTY_MESSAGE },
        )
        assertTrue("accepted levels must carry the engine transcript", speaks.all { it.transcript == "hello world" })

        val firstSpeak = results.first { it.spoken }
        assertTrue(
            "a level well above the declared ${format(thresholdDb)} dBFS must speak: $speaks",
            speaks.any { it.dbfs >= thresholdDb + 1.0 },
        )
        assertTrue(
            "gate flip ${firstSpeak.dbfs} dBFS is not within 2 dB of the declared ${format(thresholdDb)} dBFS",
            abs(firstSpeak.dbfs - thresholdDb) <= 2.0,
        )
        assertEquals("only one transition in the sweep", 1, results.zipWithNext().count { (a, b) -> a.spoken != b.spoken })
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
