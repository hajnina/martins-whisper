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
    fun merge(parts: List<String>, overlapsMs: List<Int> = emptyList()): String {
        val maxOverlapWords = 8
        fun tokenize(text: String): List<String> =
            text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun normalizedToken(token: String): String =
            token.lowercase().filter { it.isLetterOrDigit() || it == '\'' }
        fun punctuationScore(token: String): Int =
            token.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        fun richerToken(first: String, second: String): String =
            if (punctuationScore(second) > punctuationScore(first)) second else first

        val merged = mutableListOf<String>()
        for ((partIndex, part) in parts.withIndex()) {
            val tokens = tokenize(part)
            if (tokens.isEmpty()) continue
            if (merged.isEmpty()) {
                merged.addAll(tokens)
                continue
            }

            val shouldDedupe = overlapsMs.isEmpty() ||
                (overlapsMs.getOrNull(partIndex - 1) ?: 0) > 0
            var matched = 0
            if (shouldDedupe) {
                val limit = minOf(maxOverlapWords, merged.size, tokens.size)
                for (candidate in limit downTo 1) {
                    val left = merged.subList(merged.size - candidate, merged.size)
                    val right = tokens.subList(0, candidate)
                    if (left.map(::normalizedToken) == right.map(::normalizedToken)) {
                        matched = candidate
                        break
                    }
                }
            }

            if (matched > 0) {
                val start = merged.size - matched
                for (offset in 0 until matched) {
                    merged[start + offset] = richerToken(merged[start + offset], tokens[offset])
                }
                merged.addAll(tokens.drop(matched))
            } else {
                merged.addAll(tokens)
            }
        }
        return merged.joinToString(" ")
    }
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
