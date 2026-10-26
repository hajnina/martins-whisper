package com.martins.whisper.core.asr

import com.martins.whisper.core.AudioChunk
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.Transcript
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Deterministic engine for JVM/UI tests. Not for production and not registered in the service.
 */
class FakeSpeechEngine(
    override val backend: String = "fake",
    private val respond: (AudioChunk, String?) -> Transcript = { chunk, language ->
        Transcript(
            text = "chunk ${chunk.index}",
            language = language ?: "en",
            avgLogProb = -0.1f,
            noSpeechProb = 0.01f,
        )
    },
    private val closeCountProvider: () -> Unit = {},
) : SpeechEngine {

    val received = CopyOnWriteArrayList<Received>()
    val languages = CopyOnWriteArrayList<String?>()

    @Volatile
    var closed: Boolean = false
        private set

    @Volatile
    var closeCalls: Int = 0
        private set

    var failure: Throwable? = null
    var failTimes: Int = Int.MAX_VALUE

    override suspend fun transcribe(chunk: AudioChunk, language: String?): Transcript {
        check(!closed) { "engine is closed" }
        failure?.let { error ->
            if (failTimes > 0) {
                failTimes--
                throw error
            }
        }
        received += Received(chunk, language)
        languages += language
        return respond(chunk, language)
    }

    override fun close() {
        if (closed) return
        closed = true
        closeCalls++
        closeCountProvider()
    }

    data class Received(val chunk: AudioChunk, val language: String?)
}
