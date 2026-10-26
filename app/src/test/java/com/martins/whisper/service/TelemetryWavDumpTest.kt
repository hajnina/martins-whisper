package com.martins.whisper.service

import android.Manifest
import android.app.Application
import android.content.Context
import com.martins.whisper.audio.AudioSource
import com.martins.whisper.core.asr.FakeSpeechEngine
import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class TelemetryWavDumpTest {

    private lateinit var app: Application
    private lateinit var audio: EmittingAudioSource
    private lateinit var originalEngineFactory: (Context, com.martins.whisper.data.SettingsStore) -> com.martins.whisper.core.SpeechEngine
    private lateinit var originalAudioSourceFactory: (Context) -> AudioSource
    private lateinit var originalTargetCapture: () -> Unit
    private var originalWavDump = false

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        File(app.filesDir, "debug-audio").deleteRecursively()
        File(app.filesDir, "telemetry").deleteRecursively()
        audio = EmittingAudioSource()
        originalEngineFactory = EngineFactory.factory
        originalAudioSourceFactory = DictationService.audioSourceFactory
        originalTargetCapture = DictationService.targetCapture
        originalWavDump = DictationService.wavDumpEnabled
        EngineFactory.factory = { _, _ -> FakeSpeechEngine() }
        DictationService.audioSourceFactory = { audio }
        DictationService.targetCapture = {}
        DictationService.wavDumpEnabled = true
    }

    @After
    fun tearDown() {
        EngineFactory.factory = originalEngineFactory
        DictationService.audioSourceFactory = originalAudioSourceFactory
        DictationService.targetCapture = originalTargetCapture
        DictationService.wavDumpEnabled = originalWavDump
    }

    @Test
    fun `record appends one JSONL line and mirrors last_json with every protocol field`() {
        val files = runtimeFiles()
        val recorder = TelemetryRecorder(File(files, "telemetry"))
        val record = DictationRecord(
            tTapStart = 10,
            tMicOpen = 12,
            tTapStop = 2_000,
            audioMs = 1_975,
            sampleRate = 16_000,
            peakDbfs = -12.5f,
            rmsDbfs = -30.5f,
            noiseFloorDbfs = -70f,
            gateThresholdDb = 10f,
            gateVerdict = "speak",
            model = "base",
            language = "en",
            threads = 4,
            engineLoadMs = 700,
            engineInferMs = 1_200,
            textLen = 17,
            targetPackage = "com.example.app",
            targetChanged = false,
            tInserted = 2_100,
            insertMethod = "accessibility",
            result = "inserted",
            error = null,
        )
        recorder.record(record)
        val jsonl = File(files, "telemetry/records.jsonl")
        val last = File(files, "telemetry/last.json")
        assertEquals(1, jsonl.readLines().size)
        val parsed = JSONObject(jsonl.readText().trim())
        val missing = TRUTHFUL_FIELDS.filterNot { parsed.has(it) }
        assertTrue("protocol fields missing: $missing", missing.isEmpty())
        assertEquals("inserted", parsed.getString("result"))
        assertEquals(-12.5, parsed.getDouble("peak_dBFS"), 0.01)
        assertEquals(-70.0, parsed.getDouble("noise_floor_dBFS"), 0.01)
        assertTrue("null target_package must still exist in the record", parsed.has("target_package"))
        val mirrored = JSONObject(last.readText())
        assertEquals(parsed.getString("result"), mirrored.getString("result"))
        assertEquals(parsed.getString("gate_verdict"), mirrored.getString("gate_verdict"))
        assertEquals(-12.5, mirrored.getDouble("peak_dBFS"), 0.01)
        assertTrue(
            "last.json mirror must be a boolean target_changed",
            mirrored.isNull("target_changed") || !mirrored.getBoolean("target_changed"),
        )
        recorder.record(record)
        assertEquals(2, File(files, "telemetry/records.jsonl").readLines().size)
    }

    @Test
    fun `wav dump writes a valid RIFF PCM16 mono file and a json sidecar`() {
        val files = runtimeFiles()
        val samples = FloatArray(1_600) { i ->
            0.05f * kotlin.math.sin(2.0 * Math.PI * 440 * i / 16_000.0).toFloat()
        }
        val wav = DebugWavDump.dump(files, "123", samples)
        val bytes = wav.readBytes()
        assertEquals("123.wav", wav.name)
        assertTrue("WAV must start RIFF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF")
        assertEquals(samples.size * 2 + 44, bytes.size)
        val dataBytes = bytes.copyOfRange(44, bytes.size)
        assertTrue("samples must decode to nonzero PCM", dataBytes.any { it != 0.toByte() })

        DebugWavDump.writeSidecar(wav, JSONObject().put("verdict", "speak"))
        val sidecar = File(wav.parentFile, "123.json")
        assertTrue(sidecar.isFile)
        assertEquals("speak", JSONObject(sidecar.readText()).getString("verdict"))
    }

    @Test
    fun `a no-speech session still saves the wav, its sidecar and a last-json record`() {
        val files = runtimeFiles()
        val result = runSession(frames = 25, holdMs = 500)
        assertEquals(DictationState.Error::class, result::class)

        val wavs = debugWavs(files)
        assertEquals("one WAV per session", 1, wavs.size)
        val wav = wavs.single()
        assertTrue("WAV must be RIFF", String(wav.readBytes().copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF")
        val sidecar = File(wav.parentFile, "${wav.nameWithoutExtension}.json")
        assertTrue("sidecar json must exist next to the WAV", sidecar.isFile)
        val json = JSONObject(sidecar.readText())
        assertEquals("no-speech", json.getString("verdict"))
        assertTrue(json.has("peak_dBFS") && json.has("rms_dBFS") && json.has("threshold"))

        val last = File(files, "telemetry/last.json")
        assertTrue("last.json must also be written for rejected sessions", last.isFile)
        val telemetryJson = JSONObject(last.readText())
        assertEquals("no-speech", telemetryJson.getString("gate_verdict"))
        assertEquals("error", telemetryJson.getString("result"))
    }

    @Test
    fun `a too-short session still saves the captured wav and a sidecar`() {
        val files = runtimeFiles()
        runSession(frames = 5, holdMs = 100)

        val wavs = debugWavs(files)
        assertEquals("one WAV per session", 1, wavs.size)
        val sidecar = File(wavs.single().parentFile, "${wavs.single().nameWithoutExtension}.json")
        assertTrue(sidecar.isFile)
        assertEquals("too-short", JSONObject(sidecar.readText()).getString("verdict"))

        val last = File(files, "telemetry/last.json")
        assertTrue(last.isFile)
        assertEquals("cancelled", JSONObject(last.readText()).getString("result"))
    }

    private fun runSession(frames: Int, holdMs: Long): DictationState {
        val controller = Robolectric.buildService(DictationService::class.java).create()
        val service = controller.get()
        try {
            onPress(service)
            audio.emit(frames)
            ShadowSystemClock.advanceBy(holdMs, TimeUnit.MILLISECONDS)
            onRelease(service)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            return DictationService.state.value
        } finally {
            controller.destroy()
        }
    }

    private fun onPress(service: DictationService) {
        DictationService::class.java.getDeclaredMethod("onPress").apply { isAccessible = true }.invoke(service)
    }

    private fun onRelease(service: DictationService) {
        DictationService::class.java.getDeclaredMethod("onRelease").apply { isAccessible = true }.invoke(service)
    }

    private fun debugWavs(files: File): List<File> =
        File(files, "debug-audio").listFiles().orEmpty().filter { it.name.endsWith(".wav") }

    private class EmittingAudioSource : AudioSource {
        private var onFrame: ((ShortArray, Long) -> Unit)? = null

        @Synchronized
        override fun start(onFrame: (ShortArray, Long) -> Unit, onError: (Throwable) -> Unit) {
            this.onFrame = onFrame
        }

        @Synchronized
        override fun stop() = Unit

        override fun release() = Unit

        fun emit(frames: Int) {
            val callback = synchronized(this) { onFrame } ?: error("capture was not started")
            repeat(frames) { index ->
                callback(ShortArray(320) { 33 }, index * 20L)
            }
        }
    }

    private companion object {
        val TRUTHFUL_FIELDS = listOf(
            "t_tap_start", "t_mic_open", "t_tap_stop", "audio_ms", "sample_rate",
            "peak_dBFS", "rms_dBFS", "noise_floor_dBFS", "gate_threshold", "gate_verdict",
            "model", "language", "threads", "engine_load_ms", "engine_infer_ms", "text_len",
            "target_package", "target_changed", "t_inserted", "insert_method", "result", "error",
        )
    }
}

private fun runtimeFiles(): File = RuntimeEnvironment.getApplication().filesDir
