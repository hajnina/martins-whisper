package com.martins.whisper.service

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Looper
import com.martins.whisper.audio.AudioSource
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.asr.FakeSpeechEngine
import com.martins.whisper.data.SettingsStore
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSystemClock

/**
 * Host coverage for the per-session artifacts ordered in DEVICE-REPORT #2: every finished
 * dictation leaves `files/debug-audio/<stamp>.wav` + `<stamp>.json` and one
 * `files/telemetry/{records.jsonl,last.json}` record, including REJECTED sessions; a release
 * build leaves none. The `MartinsWhisperState` trace is asserted for the same session.
 *
 * Each test names the production mutation it is designed to kill.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class TelemetryWavDumpTest {

    private lateinit var app: Application
    private lateinit var audio: EmittingAudioSource
    private val inserted = CopyOnWriteArrayList<String>()
    private lateinit var originalEngineFactory: (Context, SettingsStore) -> SpeechEngine
    private lateinit var originalInserterFactory: (Context) -> TextInserter
    private lateinit var originalAudioSourceFactory: (Context) -> AudioSource
    private lateinit var originalTargetCapture: () -> Unit
    private lateinit var originalTelemetryFactory: (Context) -> DictationTelemetry?
    private var originalWavDump = false
    private var originalDebugVariant = true

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        SettingsStore(app).idleUnloadSeconds = SettingsStore.DEFAULT_IDLE_UNLOAD_SECONDS
        File(app.filesDir, "debug-audio").deleteRecursively()
        File(app.filesDir, "telemetry").deleteRecursively()

        audio = EmittingAudioSource()
        inserted.clear()
        originalEngineFactory = EngineFactory.factory
        originalInserterFactory = DictationService.inserterFactory
        originalAudioSourceFactory = DictationService.audioSourceFactory
        originalTargetCapture = DictationService.targetCapture
        originalTelemetryFactory = DictationService.telemetryFactory
        originalWavDump = DictationService.wavDumpEnabled
        originalDebugVariant = DictationService.debugVariant

        EngineFactory.factory = { _, _ -> FakeSpeechEngine() }
        DictationService.inserterFactory = {
            object : TextInserter {
                override fun insert(text: String): InsertResult {
                    inserted += text
                    return InsertResult.Inserted
                }
            }
        }
        DictationService.audioSourceFactory = { audio }
        DictationService.targetCapture = {}
        DictationService.telemetryFactory = originalTelemetryFactory
        DictationService.wavDumpEnabled = true
        DictationService.debugVariant = true
    }

    @After
    fun tearDown() {
        EngineFactory.factory = originalEngineFactory
        DictationService.inserterFactory = originalInserterFactory
        DictationService.audioSourceFactory = originalAudioSourceFactory
        DictationService.targetCapture = originalTargetCapture
        DictationService.telemetryFactory = originalTelemetryFactory
        DictationService.wavDumpEnabled = originalWavDump
        DictationService.debugVariant = originalDebugVariant
        File(app.filesDir, "debug-audio").deleteRecursively()
        File(app.filesDir, "telemetry").deleteRecursively()
    }

    @Test
    fun `record appends one JSONL line and mirrors last_json with every protocol field`() {
        // MUTATION KILLED: dropping a protocol field from toJson(), or not mirroring last.json.
        val files = app.filesDir
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
        // MUTATION KILLED: wrong RIFF/chunk sizes, wrong sample rate/channels/bits, or a sidecar
        // named differently from its WAV (breaks the owner's replay pairing).
        val samples = FloatArray(1_600) { i ->
            0.05f * kotlin.math.sin(2.0 * Math.PI * 440 * i / 16_000.0).toFloat()
        }
        val wav = DebugWavDump.dump(app.filesDir, "123", samples)
        val bytes = wav.readBytes()
        assertEquals("123.wav", wav.name)
        assertEquals("RIFF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals("fmt ", String(bytes.copyOfRange(12, 16), Charsets.US_ASCII))
        assertEquals(16_000, intLe(bytes, 24))
        assertEquals(1, intLe(bytes, 22) and 0xFFFF)
        assertEquals(16, intLe(bytes, 34) and 0xFFFF)
        assertEquals(samples.size * 2 + 44, bytes.size)
        assertTrue("samples must decode to nonzero PCM", bytes.copyOfRange(44, bytes.size).any { it != 0.toByte() })

        DebugWavDump.writeSidecar(wav, JSONObject().put("verdict", "speak"))
        val sidecar = File(wav.parentFile, "123.json")
        assertTrue(sidecar.isFile)
        assertEquals("speak", JSONObject(sidecar.readText()).getString("verdict"))
    }

    @Test
    fun `a no-speech session still saves the wav, its sidecar and a last-json record`() {
        // MUTATION KILLED: gating the dump/sidecar/telemetry on speech, or returning before them
        // on the no-speech branch (the device bug: no files after a rejected run).
        val files = app.filesDir
        val result = runSession(frames = 25, holdMs = 500, amplitude = 33)
        assertEquals(DictationState.Error::class, result::class)

        val wavs = debugWavs(files)
        assertEquals("one WAV per session", 1, wavs.size)
        val wav = wavs.single()
        assertEquals("RIFF", String(wav.readBytes().copyOfRange(0, 4), Charsets.US_ASCII))
        val sidecar = File(wav.parentFile, "${wav.nameWithoutExtension}.json")
        assertTrue("sidecar json must exist next to the WAV", sidecar.isFile)
        val json = JSONObject(sidecar.readText())
        assertEquals("no-speech", json.getString("verdict"))
        assertTrue(json.has("peak_dBFS") && json.has("rms_dBFS") && json.has("threshold"))
        assertEquals("", json.getString("transcript"))
        assertTrue(json.getString("error").startsWith("Didn't catch"))

        val last = File(files, "telemetry/last.json")
        assertTrue("last.json must also be written for rejected sessions", last.isFile)
        val telemetry = JSONObject(last.readText())
        assertEquals("no-speech", telemetry.getString("gate_verdict"))
        assertEquals("error", telemetry.getString("result"))
        assertTrue(telemetry.getString("error").startsWith("Didn't catch"))
        assertEquals(1, File(files, "telemetry/records.jsonl").readLines().size)

        val events = stateEvents()
        assertTrue("tap start must be traced: $events", events.any { it.startsWith("tap-start") })
        assertTrue("capture start must be traced: $events", events.any { it.startsWith("capture-start") })
        assertTrue("capture stop must be traced: $events", events.any { it.startsWith("capture-stop") })
        assertTrue("tap stop must be traced: $events", events.any { it.startsWith("tap-stop") })
        assertTrue("state transitions must be traced: $events", events.any { it.startsWith("state=Loading") })
        val gate = events.indexOfFirst { it.startsWith("gate=no-speech") }
        val error = events.indexOfFirst { it.startsWith("state=Error") }
        assertTrue("gate verdict must be traced before Error: $events", gate >= 0 && error > gate)
    }

    @Test
    fun `a silent-mic session also saves wav sidecar and telemetry`() {
        // MUTATION KILLED: special-casing only the no-speech branch and skipping the artifacts on
        // the digital-silence branch.
        val files = app.filesDir
        val result = runSession(frames = 25, holdMs = 500, amplitude = 0)
        assertEquals(DictationState.Error::class, result::class)

        val wav = debugWavs(files).single()
        val sidecar = File(wav.parentFile, "${wav.nameWithoutExtension}.json")
        assertEquals("silent-mic", JSONObject(sidecar.readText()).getString("verdict"))
        val last = JSONObject(File(files, "telemetry/last.json").readText())
        assertEquals("silent-mic", last.getString("gate_verdict"))
        assertEquals("error", last.getString("result"))
        assertTrue(stateEvents().any { it.startsWith("gate=silent-mic") })
    }

    @Test
    fun `a successful session records the transcript in sidecar and telemetry`() {
        // MUTATION KILLED: writing the sidecar only on failure, or losing the inserted text in
        // either artifact.
        val files = app.filesDir
        val result = runSession(frames = 25, holdMs = 500, amplitude = 6_000)
        assertEquals(DictationState.Success::class, result::class)
        assertEquals(listOf("chunk 0"), inserted.toList())

        val wav = debugWavs(files).single()
        val json = JSONObject(File(wav.parentFile, "${wav.nameWithoutExtension}.json").readText())
        assertEquals("transcribed", json.getString("verdict"))
        assertEquals("chunk 0", json.getString("transcript"))
        assertEquals("chunk 0", json.getString("inserted_text"))
        val telemetry = JSONObject(File(files, "telemetry/last.json").readText())
        assertEquals("speak", telemetry.getString("gate_verdict"))
        assertEquals("inserted", telemetry.getString("result"))
        assertEquals(7, telemetry.getInt("text_len"))
        val events = stateEvents()
        assertTrue(events.any { it.startsWith("gate=speak") })
        assertTrue(events.any { it.startsWith("insert result=") })
        assertTrue(events.any { it.startsWith("state=Success") })
    }

    @Test
    fun `a too-short session still saves the captured wav and a sidecar`() {
        // MUTATION KILLED: early return before dumpSessionAudio/sidecar/telemetry on the
        // too-short path (owner taps too briefly; DEVICE-REPORT wants every session saved).
        val files = app.filesDir
        val result = runSession(frames = 5, holdMs = 100, amplitude = 33)
        assertEquals(DictationState.Idle, result)

        val wavs = debugWavs(files)
        assertEquals("one WAV per session", 1, wavs.size)
        val sidecar = File(wavs.single().parentFile, "${wavs.single().nameWithoutExtension}.json")
        assertTrue(sidecar.isFile)
        assertEquals("too-short", JSONObject(sidecar.readText()).getString("verdict"))

        val last = File(files, "telemetry/last.json")
        assertTrue(last.isFile)
        assertEquals("cancelled", JSONObject(last.readText()).getString("result"))
    }

    @Test
    fun `release variant writes no wav no sidecar and no telemetry even after a full session`() {
        // MUTATION KILLED: removing `&& debugVariant` from wavDumpEnabled() or the release guard
        // from the default telemetryFactory (personal audio/telemetry leaking into release).
        DictationService.debugVariant = false
        DictationService.wavDumpEnabled = true
        val result = runSession(frames = 25, holdMs = 500, amplitude = 6_000)
        assertEquals(DictationState.Success::class, result::class)

        assertFalse("release must not create debug-audio", File(app.filesDir, "debug-audio").exists())
        assertFalse("release must not create telemetry", File(app.filesDir, "telemetry").exists())
    }

    private fun runSession(frames: Int, holdMs: Long, amplitude: Int): DictationState {
        val controller = Robolectric.buildService(DictationService::class.java).create()
        val service = controller.get()
        try {
            onPress(service)
            audio.emit(frames, amplitude)
            ShadowSystemClock.advanceBy(holdMs, TimeUnit.MILLISECONDS)
            onRelease(service)
            awaitTerminal()
            return DictationService.state.value
        } finally {
            controller.destroy()
        }
    }

    private fun awaitTerminal() {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val state = DictationService.state.value
            // Idle after the release can only be the too-short path; the reset timer is 1.8 s.
            if (state is DictationState.Success || state is DictationState.Error || state is DictationState.Idle) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out; state=${DictationService.state.value}")
    }

    private fun onPress(service: DictationService) {
        DictationService::class.java.getDeclaredMethod("onPress").apply { isAccessible = true }.invoke(service)
    }

    private fun onRelease(service: DictationService) {
        DictationService::class.java.getDeclaredMethod("onRelease").apply { isAccessible = true }.invoke(service)
    }

    private fun debugWavs(files: File): List<File> =
        File(files, "debug-audio").listFiles().orEmpty().filter { it.name.endsWith(".wav") }

    private fun stateEvents(): List<String> =
        ShadowLog.getLogsForTag(DictationService.STATE_TAG).map { it.msg }

    private fun intLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private class EmittingAudioSource : AudioSource {
        var started = false
            private set
        private var onFrame: ((ShortArray, Long) -> Unit)? = null

        @Synchronized
        override fun start(onFrame: (ShortArray, Long) -> Unit, onError: (Throwable) -> Unit) {
            started = true
            this.onFrame = onFrame
        }

        @Synchronized
        override fun stop() = Unit

        override fun release() = Unit

        fun emit(frames: Int, amplitude: Int) {
            val callback = synchronized(this) { onFrame } ?: error("capture was not started")
            repeat(frames) { index ->
                callback(ShortArray(320) { amplitude.toShort() }, index * 20L)
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
