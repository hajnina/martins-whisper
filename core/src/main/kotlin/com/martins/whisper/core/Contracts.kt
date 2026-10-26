package com.martins.whisper.core

import com.martins.whisper.core.text.Graphemes
import com.martins.whisper.core.text.TextScript
import java.text.Normalizer

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
    /**
     * Fits spoken text into the text around [selectionStart]: trims whisper's own padding, adds a
     * leading space only when the language uses word spaces and the previous character is not
     * whitespace, and applies case only for cased scripts. CJK/Thai get neither a space nor
     * capitalisation; RTL and Indic scripts keep spaces but are never re-cased.
     *
     * [language] is the whisper code of the detected/selected language; null means "infer the
     * script from the text itself".
     */
    fun formatForCaret(
        insert: String,
        current: String?,
        selectionStart: Int,
        language: String? = null,
    ): String {
        val text = insert.trimUnicode()
        if (text.isEmpty()) return ""
        val script = TextScript.forLanguage(language) ?: TextScript.forText(text)
        val base = current.orEmpty()
        val lastLine = if (base.isBlank()) {
            ""
        } else {
            base.substring(0, selectionStart.coerceIn(0, base.length))
                .substringAfterLast('\n')
        }
        val cased = if (!script.cased) {
            text
        } else if (lastLine.isBlank()) {
            text.capitalizeFirstLetter()
        } else {
            val startsSentence = lastLine.trimEnd().lastOrNull() in setOf('.', '!', '?', '\n')
            if (startsSentence) text.capitalizeFirstLetter() else text.lowercaseFirstLetter()
        }
        if (lastLine.isBlank()) return cased
        val prefix = if (script.wordSpaces && !lastLine.last().isWhitespace()) " " else ""
        return prefix + cased
    }

    /** Removes whisper's padding, including non-breaking and ideographic spaces. */
    private fun String.trimUnicode(): String {
        var start = 0
        var end = length
        while (start < end && isUnicodeSpace(this[start])) start++
        while (end > start && isUnicodeSpace(this[end - 1])) end--
        return substring(start, end)
    }

    private fun isUnicodeSpace(char: Char): Boolean =
        char.isWhitespace() || Character.isSpaceChar(char)

    private fun String.capitalizeFirstLetter(): String =
        mapFirstLetter { letter ->
            if (letter.first().isLowerCase()) {
                letter.first().titlecase() + letter.drop(1)
            } else {
                letter
            }
        }

    private fun String.lowercaseFirstLetter(): String =
        mapFirstLetter { letter ->
            if (letter.first().isUpperCase()) {
                letter.first().lowercase() + letter.drop(1)
            } else {
                letter
            }
        }

    private fun String.mapFirstLetter(transform: (String) -> String): String {
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            if (Character.isLetter(codePoint)) {
                val original = String(Character.toChars(codePoint))
                val replaced = transform(original)
                if (replaced == original) return this
                return substring(0, index) + replaced +
                    substring(index + Character.charCount(codePoint))
            }
            index += Character.charCount(codePoint)
        }
        return this
    }

    /** NFC-normalised form, used when comparing inserted text with what the field read back. */
    fun normalizeNfc(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC)

    /** True when both texts are equal after NFC normalisation (NFC/NFD must not look different). */
    fun normalizedEquals(expected: String?, actual: String?): Boolean =
        expected != null && actual != null && normalizeNfc(expected) == normalizeNfc(actual)

    fun insert(
        current: String?,
        selectionStart: Int,
        selectionEnd: Int,
        insert: String,
    ): Result {
        val base = current.orEmpty()
        val rawStart = selectionStart.coerceIn(0, base.length)
        val rawEnd = selectionEnd.coerceIn(rawStart, base.length)

        // Never split a UTF-16 pair or a grapheme cluster: a caret/selection edge inside one is
        // expanded to the whole cluster, matching the previous surrogate-pair behaviour.
        val start = Graphemes.clusterStart(base, rawStart)
        val end = Graphemes.clusterEnd(base, rawEnd).coerceAtLeast(start)

        val text = base.substring(0, start) + insert + base.substring(end)
        val caret = start + insert.length
        return Result(text, caret, caret)
    }
}
