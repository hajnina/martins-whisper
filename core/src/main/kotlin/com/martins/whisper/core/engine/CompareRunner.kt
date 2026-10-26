package com.martins.whisper.core.engine

import com.martins.whisper.core.AudioChunk
import com.martins.whisper.core.ChunkReason
import com.martins.whisper.core.SpeechEngine
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs the same PCM buffer through every AVAILABLE engine, one at a time, and returns one
 * [EngineResult] per engine in registry order. Sequential by contract: two on-device runtimes
 * loading and inferring at once would fight for the same CPU/RAM and distort the timings.
 *
 * Engine creation is counted as load time, inference separately. An engine that fails to open or
 * transcribe produces an error result instead of aborting the compare, and its engine is closed.
 * The clock is injectable so host tests assert timings without sleeping.
 */
class CompareRunner(
    private val registry: EngineRegistry,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {

    suspend fun compare(
        pcm: FloatArray,
        sampleRate: Int = 16_000,
        language: String? = null,
    ): List<EngineResult> {
        require(sampleRate > 0) { "sampleRate must be > 0" }
        val audioMs = if (pcm.isEmpty()) 0L else pcm.size * 1000L / sampleRate
        val available = registry.statuses().filter { it.available }
        val results = ArrayList<EngineResult>(available.size)
        for (status in available) {
            results += runOne(status.engineId, pcm, sampleRate, audioMs, language)
        }
        return results
    }

    private suspend fun runOne(
        engineId: String,
        pcm: FloatArray,
        sampleRate: Int,
        audioMs: Long,
        language: String?,
    ): EngineResult {
        val loadStart = clock()
        val engine: SpeechEngine = try {
            registry.open(engineId)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return EngineResult(
                engineId = engineId,
                text = "",
                loadMs = elapsedSince(loadStart),
                inferMs = 0L,
                audioMs = audioMs,
                error = t.message ?: t::class.java.simpleName,
            )
        }
        val loadMs = elapsedSince(loadStart)

        val inferStart = clock()
        return try {
            val transcript = engine.transcribe(
                AudioChunk(
                    index = 0,
                    samples = pcm,
                    sampleRate = sampleRate,
                    startMs = 0L,
                    endMs = audioMs,
                    reason = ChunkReason.FLUSH,
                ),
                language = language,
            )
            EngineResult(
                engineId = engineId,
                text = transcript.text,
                loadMs = loadMs,
                inferMs = elapsedSince(inferStart),
                audioMs = audioMs,
                error = null,
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            EngineResult(
                engineId = engineId,
                text = "",
                loadMs = loadMs,
                inferMs = elapsedSince(inferStart),
                audioMs = audioMs,
                error = t.message ?: t::class.java.simpleName,
            )
        } finally {
            runCatching { engine.close() }
        }
    }

    private fun elapsedSince(startMs: Long): Long = (clock() - startMs).coerceAtLeast(0L)
}
