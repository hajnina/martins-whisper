package com.martins.whisper.core.engine

import com.martins.whisper.core.AudioChunk
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.Transcript
import java.util.concurrent.CopyOnWriteArrayList

/**
 * TEST-ONLY stand-in for the on-device Gemma E2B engine. It is never registered in production
 * wiring ([com.martins.whisper.service.EngineFactory] does not know it).
 *
 * Construction represents loading, so [loadLatencyMs] (advanced on the injected clock) models
 * model load time. [inferLatencyMs] is advanced inside [transcribe]. The clock callback keeps
 * host tests deterministic and makes CompareRunner timings assertable without sleeping.
 */
class FakeGemmaEngine(
    override val backend: String = EngineIds.GEMMA_E2B,
    private val reply: String = "gemma fake transcript",
    private val loadLatencyMs: Long = 0L,
    private val inferLatencyMs: Long = 0L,
    private val advanceClock: (Long) -> Unit = {},
    private val failWith: Throwable? = null,
    private val onClose: () -> Unit = {},
) : SpeechEngine {

    init {
        require(loadLatencyMs >= 0L) { "loadLatencyMs must be >= 0" }
        require(inferLatencyMs >= 0L) { "inferLatencyMs must be >= 0" }
        advanceClock(loadLatencyMs)
    }

    val received: MutableList<Pair<AudioChunk, String?>> = CopyOnWriteArrayList()

    @Volatile
    var closeCalls: Int = 0
        private set

    override suspend fun transcribe(chunk: AudioChunk, language: String?): Transcript {
        advanceClock(inferLatencyMs)
        failWith?.let { throw it }
        received += chunk to language
        return Transcript(text = reply, language = language ?: "en")
    }

    override fun close() {
        closeCalls++
        onClose()
    }
}
