package com.martins.whisper.core.engine

import com.martins.whisper.core.Transcript
import com.martins.whisper.core.asr.FakeSpeechEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareRunnerTest {

    /** Deterministic millisecond clock: engine fakes advance it instead of sleeping. */
    private class FakeClock {
        var now: Long = 0L
        fun advance(ms: Long) {
            now += ms
        }
    }

    private fun registration(
        id: String,
        modelInstalled: Boolean = true,
        runtimePresent: Boolean = true,
        engine: () -> com.martins.whisper.core.SpeechEngine,
    ) = EngineRegistration(
        id = id,
        displayName = id,
        probe = { EngineProbe(modelInstalled = modelInstalled, runtimePresent = runtimePresent) },
        factory = engine,
    )

    private fun registry(vararg registrations: EngineRegistration) =
        EngineRegistry(registrations.toList(), InMemoryEnginePreferences())

    private val oneSecondPcm = FloatArray(16_000) { 0.1f }

    @Test
    fun `two engines with different latencies produce ordered timings and per-engine rtf`() = runTest {
        val clock = FakeClock()
        val whisper = FakeSpeechEngine(
            backend = "whisper",
            respond = { _, language ->
                clock.advance(90)
                Transcript(text = "whisper text", language = language ?: "en")
            },
        )
        val runner = CompareRunner(
            registry(
                registration(EngineIds.WHISPER) { whisper },
                registration(EngineIds.GEMMA_E2B) {
                    FakeGemmaEngine(
                        reply = "gemma text",
                        loadLatencyMs = 40,
                        inferLatencyMs = 400,
                        advanceClock = clock::advance,
                    )
                },
            ),
            clock = { clock.now },
        )

        val results = runner.compare(oneSecondPcm, language = "cs")

        assertEquals(listOf(EngineIds.WHISPER, EngineIds.GEMMA_E2B), results.map { it.engineId })
        assertEquals("whisper text", results[0].text)
        assertEquals(0L, results[0].loadMs)
        assertEquals(90L, results[0].inferMs)
        assertEquals(1_000L, results[0].audioMs)
        assertEquals(0.09, results[0].realTimeFactor, 0.000_001)
        assertNull(results[0].error)

        assertEquals("gemma text", results[1].text)
        assertEquals(40L, results[1].loadMs)
        assertEquals(400L, results[1].inferMs)
        assertEquals(1_000L, results[1].audioMs)
        assertEquals(0.4, results[1].realTimeFactor, 0.000_001)
        assertNull(results[1].error)
        // Mutation killed: swapping load/infer measurement, using total (load+infer) as RTF,
        // reporting registry order from a hash map, or using the real wall clock (timings would
        // be ~0 and non-deterministic).
    }

    @Test
    fun `a failing engine returns an error result without aborting the other engines`() = runTest {
        val clock = FakeClock()
        val whisper = FakeSpeechEngine(
            backend = "whisper",
            respond = { _, language ->
                clock.advance(75)
                Transcript(text = "whisper survives", language = language ?: "en")
            },
        )
        lateinit var brokenGemma: FakeGemmaEngine
        val runner = CompareRunner(
            registry(
                registration(EngineIds.WHISPER) { whisper },
                registration(EngineIds.GEMMA_E2B) {
                    FakeGemmaEngine(
                        loadLatencyMs = 30,
                        inferLatencyMs = 12,
                        advanceClock = clock::advance,
                        failWith = IllegalStateException("gemma boom"),
                    ).also { brokenGemma = it }
                },
            ),
            clock = { clock.now },
        )

        val results = runner.compare(oneSecondPcm)

        assertEquals(2, results.size)
        assertEquals("whisper survives", results[0].text)
        assertNull(results[0].error)
        val failed = results[1]
        assertEquals(EngineIds.GEMMA_E2B, failed.engineId)
        assertEquals("", failed.text)
        assertEquals("gemma boom", failed.error)
        assertEquals(30L, failed.loadMs)
        assertEquals(12L, failed.inferMs)
        assertEquals(0.012, failed.realTimeFactor, 0.000_001)
        assertEquals(1, brokenGemma.closeCalls)
        // Mutation killed: rethrowing the engine failure (whole compare lost), leaving the
        // failed engine's text populated, not closing a half-used engine, or dropping the
        // timings measured before the failure.
    }

    @Test
    fun `engines that are not available are skipped instead of run`() = runTest {
        val clock = FakeClock()
        val gemma = FakeGemmaEngine(reply = "must not run", advanceClock = clock::advance)
        val runner = CompareRunner(
            registry(
                registration(EngineIds.WHISPER) { FakeSpeechEngine(backend = "whisper") },
                registration(EngineIds.GEMMA_E2B, modelInstalled = false) { gemma },
            ),
            clock = { clock.now },
        )

        val results = runner.compare(oneSecondPcm)

        assertEquals(listOf(EngineIds.WHISPER), results.map { it.engineId })
        assertEquals(0, gemma.closeCalls)
        // Mutation killed: running every registered engine instead of only AVAILABLE ones
        // (would report a bogus model-missing error for an engine the user cannot use).
    }

    @Test
    fun `a failure while opening the engine is reported as a load failure`() = runTest {
        val runner = CompareRunner(
            registry(
                registration(EngineIds.GEMMA_E2B) { error("cannot open gemma") },
            ),
            clock = { 0L },
        )

        val result = runner.compare(oneSecondPcm).single()

        assertEquals("", result.text)
        assertEquals("cannot open gemma", result.error)
        assertEquals(0L, result.inferMs)
        assertEquals(0.0, result.realTimeFactor, 0.000_001)
        // Mutation killed: counting an open failure as inference time or crashing the compare.
    }

    @Test
    fun `empty audio does not divide by zero and still runs available engines`() = runTest {
        val result = CompareRunner(
            registry(registration(EngineIds.WHISPER) { FakeSpeechEngine(backend = "whisper") }),
            clock = { 0L },
        ).compare(FloatArray(0)).single()

        assertEquals(0L, result.audioMs)
        assertEquals(0.0, result.realTimeFactor, 0.000_001)
        assertNotNull(result.text)
        // Mutation killed: NaN/Infinity RTF for an empty buffer reaching the sidecar JSON.
    }
}
