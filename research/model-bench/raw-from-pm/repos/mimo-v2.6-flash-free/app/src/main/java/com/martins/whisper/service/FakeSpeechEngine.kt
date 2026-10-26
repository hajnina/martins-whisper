package com.martins.whisper.service

import com.martins.whisper.core.AudioChunk
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.Transcript
import kotlinx.coroutines.delay

/**
 * Deterministic stand-in for E1's whisper.cpp engine. It exercises the full
 * press -> record -> finalize -> insert path without native code or a model file.
 */
class FakeSpeechEngine(
    private val loadDelayMs: Long = 180,
    private val transcribeDelayMs: Long = 350,
) : SpeechEngine {

    @Volatile
    private var closed = false

    override val backend: String = "fake/cpu"

    suspend fun load() {
        if (closed) return
        if (loadDelayMs > 0) delay(loadDelayMs)
    }

    override suspend fun transcribe(chunk: AudioChunk, language: String?): Transcript {
        if (transcribeDelayMs > 0) delay(transcribeDelayMs)
        if (closed) return Transcript(text = "")
        val seconds = (chunk.endMs - chunk.startMs) / 1000.0
        val lang = language ?: "auto"
        return Transcript(
            text = "Fake transcript of ${"%.1f".format(seconds)} s ($lang).",
            language = language,
            avgLogProb = -0.2f,
            noSpeechProb = 0.01f,
        )
    }

    override fun close() {
        closed = true
    }
}
