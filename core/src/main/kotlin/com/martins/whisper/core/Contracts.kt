package com.martins.whisper.core

/**
 * Shared API contract for core logic. Implementations live in sibling files and are
 * owned by individual work packages; do not change these signatures without PM review.
 */

data class Frame(
    val samples: ShortArray,
    val sampleRate: Int = 16_000,
    val capturedAtMs: Long,
)

data class AudioChunk(
    val index: Int,
    val samples: FloatArray,
    val sampleRate: Int = 16_000,
    val startMs: Long,
    val endMs: Long,
    val overlapMs: Int = 0,
    val reason: ChunkReason,
)

enum class ChunkReason { SILENCE, MAX_LENGTH, FLUSH }

interface Vad {
    fun isSpeech(frame: ShortArray): Boolean
    fun reset()
}

interface AudioChunkerApi {
    fun push(frame: ShortArray, capturedAtMs: Long): List<AudioChunk>
    fun flush(capturedAtMs: Long): AudioChunk?
    fun reset()
    val speechMs: Long
}

data class Transcript(
    val text: String,
    val language: String? = null,
    val avgLogProb: Float? = null,
    val noSpeechProb: Float? = null,
    val compressionRatio: Float? = null,
)

interface SpeechEngine {
    val backend: String
    suspend fun transcribe(chunk: AudioChunk, language: String? = null): Transcript
    fun close()
}

enum class ComputeBackend { AUTO, GPU, CPU }

data class ModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String?,
    val sha256: String?,
    val sizeBytes: Long?,
    val multilingual: Boolean,
    val notes: String = "",
)

enum class ModelState { UNLOADED, LOADING, READY, ERROR }

object TranscriptMerger {
    /**
     * Merge chunk transcripts in order, removing duplicated boundary words for overlapped
     * chunks. Implemented by C1.
     */
    fun merge(parts: List<String>, overlapsMs: List<Int> = emptyList()): String =
        parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
}

object TextInsertionPlanner {
    data class Result(val text: String, val selectionStart: Int, val selectionEnd: Int)

    /**
     * Insert [insert] into [current] replacing [selectionStart]..[selectionEnd]. Must clamp
     * out-of-range selections and handle null/empty current text. Implemented by I1.
     */
    fun insert(
        current: String?,
        selectionStart: Int,
        selectionEnd: Int,
        insert: String,
    ): Result {
        val base = current.orEmpty()
        val start = selectionStart.coerceIn(0, base.length)
        val end = selectionEnd.coerceIn(start, base.length)
        val text = base.substring(0, start) + insert + base.substring(end)
        val caret = start + insert.length
        return Result(text, caret, caret)
    }
}
