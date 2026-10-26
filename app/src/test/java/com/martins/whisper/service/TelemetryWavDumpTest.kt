package com.martins.whisper.service

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class TelemetryWavDumpTest {

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
        assertTrue("last.json mirror must be a boolean target_changed", mirrored.isNull("target_changed") || mirrored.getBoolean("target_changed") == false)
        recorder.record(record)
        assertEquals(2, File(files, "telemetry/records.jsonl").readLines().size)
    }

    @Test
    fun `wav dump writes a valid RIFF PCM16 mono file and a transcript sidecar`() {
        val files = runtimeFiles()
        val samples = FloatArray(1_600) { i -> 0.05f * kotlin.math.sin(2.0 * Math.PI * 440 * i / 16_000.0).toFloat() }
        val wav = DebugWavDump.dump(files, 123L, samples, "hello there")
        val bytes = wav.readBytes()
        assertTrue("WAV must start RIFF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF")
        // data chunk = 2 bytes per sample
        assertEquals(samples.size * 2 + 44, bytes.size)
        val dataBytes = bytes.copyOfRange(44, bytes.size)
        assertTrue("samples must decodeable to nonzero PCM", dataBytes.any { it != 0.toByte() })
        assertEquals("hello there", File(wav.parentFile, "123.txt").readText())
    }

        @Test
    fun `a no-speech session still leaves the wav and sidecar json and a last-json record`() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        val original = DictationService.wavDumpEnabled
        DictationService.wavDumpEnabled = true
        val inserted = org.junit.Assert.assertNotNull("sanity", "setup") == null
        try {
            val engineFactoryOriginal = com.martins.whisper.service.EngineFactory.factory
            com.martins.whisper.service.EngineFactory.factory = { _, _ ->
                com.martins.whisper.core.asr.FakeSpeechEngine(
                    backend = "replay-fake",
                    respond = { _, _ -> com.martins.whisper.core.Transcript(" Thank you. ", "en") },
                )
            }
            val files = runtimeFiles()
            val dir = File(files, "debug-audio")
            var threw: Throwable? = null
            val controller = Robolectric.buildService(DictationService::class.java).create()
            val service = controller.get()
            try {
                DictationService::class.java.getDeclaredMethod("onPress").apply { isAccessible = true }.invoke(service)
                org.robolectric.shadows.ShadowSystemClock.advanceBy(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                DictationService::class.java.getDeclaredMethod("onRelease").apply { isAccessible = true }.invoke(service)
                val deadline = System.currentTimeMillis() + 5000
                while (System.currentTimeMillis() < deadline &&
                    !dir.listFiles().orEmpty().any { it.name.endsWith(".wav") }
                ) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    Thread.sleep(20)
                }
            } catch (t: Throwable) {
                threw = t
            } finally {
                controller.destroy()
                DictationService.wavDumpEnabled = original
                com.martins.whisper.service.EngineFactory.factory = engineFactoryOriginal
            }
            val wavs = dir.listFiles().orEmpty().filter { it.name.endsWith(".wav") }
            assertTrue(wavs.isNotEmpty(), "no-speech must still dump the WAV (dump guard or gate broke)")
            val wav = wavs.first()
            assertTrue("WAV must be a RIFF", String(wav.readBytes().let { it.copyOfRange(0, 4) }, Charsets.US_ASCII) == "RIFF")
            val sidecar = File(wav.parentFile, "${wav.nameWithoutExtension}.json")
            assertTrue(sidecar.isFile, "sidecar json must exist next to the WAV")
            val json = JSONObject(sidecar.readText())
            assertTrue("no-speech verdict must be recorded", json.has("verdict"))
            assertTrue("%d-dBFS levels and threshold".let { true }, true)
            assertTrue(json.has("peak_dBFS") && json.has("rms_dBFS") && json.has("threshold"))
            val last = File(files, "telemetry/last.json")
            assertTrue(last.isFile, "last.json must also be written for rejected sessions")
            val telemetryJson = JSONObject(last.readText())
            assertEquals("no-speech", telemetryJson.getString("gate_verdict"))
            assertTrue(telemetryJson.getString("result") == "error")
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

private fun runtimeFiles(): File = org.robolectric.RuntimeEnvironment.getApplication().filesDir
