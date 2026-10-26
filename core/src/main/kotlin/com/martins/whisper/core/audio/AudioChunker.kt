package com.martins.whisper.core.audio

import com.martins.whisper.core.AudioChunk
import com.martins.whisper.core.AudioChunkerApi
import com.martins.whisper.core.ChunkReason
import com.martins.whisper.core.Vad
import kotlin.math.max
import kotlin.math.min

class AudioChunker(
    private val vad: Vad = EnergyVad(),
) : AudioChunkerApi {
    private data class BufferedFrame(
        val samples: ShortArray,
        val startMs: Long,
        val endMs: Long,
    )

    private val preRoll = ArrayDeque<BufferedFrame>()
    private var chunk = mutableListOf<BufferedFrame>()
    private var active = false
    private var speechMsInChunk = 0L
    private var silenceMs = 0L
    private var speechFloorRms = MIN_AUDIBLE_RMS
    private var pendingOverlapMs = 0
    private var nextIndex = 0
    private var sessionStartMs: Long? = null
    private var sessionCapped = false
    private var totalSpeechMs = 0L

    override val speechMs: Long
        get() = totalSpeechMs

    override fun push(frame: ShortArray, capturedAtMs: Long): List<AudioChunk> {
        if (frame.isEmpty() || sessionCapped) return emptyList()
        if (sessionStartMs == null) sessionStartMs = capturedAtMs

        var offset = 0
        val output = mutableListOf<AudioChunk>()
        while (offset < frame.size) {
            val size = min(FRAME_SAMPLES, frame.size - offset)
            val startMs = capturedAtMs + offset * 1000L / SAMPLE_RATE
            val samples = frame.copyOfRange(offset, offset + size)
            output += processFrame(samples, startMs)
            offset += size
            if (sessionCapped) break
        }
        return output
    }

    private fun processFrame(samples: ShortArray, capturedAtMs: Long): List<AudioChunk> {
        val sessionStart = sessionStartMs ?: capturedAtMs.also { sessionStartMs = it }
        if (capturedAtMs >= sessionStart + SESSION_CAP_MS) {
            sessionCapped = true
            val finalChunk = if (active && chunk.isNotEmpty()) {
                buildChunk(chunk.toList(), ChunkReason.FLUSH, pendingOverlapMs)
            } else {
                null
            }
            clearActiveState()
            return listOfNotNull(finalChunk)
        }

        val durationMs = samples.size * 1000L / SAMPLE_RATE
        val frameRms = AudioMath.rms(samples)
        val buffered = BufferedFrame(samples, capturedAtMs, capturedAtMs + durationMs)
        val speech = vad.isSpeech(samples)

        if (!active) {
            if (!speech) {
                appendPreRoll(buffered)
                return emptyList()
            }
            active = true
            chunk = preRoll.toMutableList()
            preRoll.clear()
            chunk.add(buffered)
            speechFloorRms = max(frameRms * 0.10, MIN_AUDIBLE_RMS)
            speechMsInChunk = if (isAudible(frameRms)) durationMs else 0L
            silenceMs = if (isAudible(frameRms)) 0L else durationMs
            if (isAudible(frameRms)) totalSpeechMs += durationMs
            pendingOverlapMs = 0
        } else {
            chunk.add(buffered)
            val audible = isAudible(frameRms)
            if (speech && audible) {
                speechMsInChunk += durationMs
                totalSpeechMs += durationMs
                silenceMs = 0L
            } else {
                silenceMs += durationMs
            }
        }

        if (chunk.isNotEmpty() && chunkDurationMs() >= MAX_CHUNK_MS) {
            return listOf(splitAtMaxLength())
        }

        if (silenceMs >= SILENCE_FINALIZE_MS) {
            return finalizeSilence()
        }
        return emptyList()
    }

    private fun isAudible(rms: Double): Boolean = rms >= speechFloorRms

    private fun chunkDurationMs(): Long {
        if (chunk.isEmpty()) return 0L
        return chunk.last().endMs - chunk.first().startMs
    }

    private fun finalizeSilence(): List<AudioChunk> {
        val hasMinimumSpeech = speechMsInChunk >= MIN_SPEECH_MS
        val frames = chunk.toList()
        val output = if (hasMinimumSpeech) {
            listOf(buildChunk(frames, ChunkReason.SILENCE, pendingOverlapMs))
        } else {
            emptyList()
        }
        retainPreRoll(frames)
        clearActiveState()
        return output
    }

    private fun splitAtMaxLength(): AudioChunk {
        val lastEnd = chunk.last().endMs
        val windowStart = lastEnd - MAX_CUT_SEARCH_MS
        var cutIndex = chunk.lastIndex
        var lowestEnergy = Double.MAX_VALUE
        for (index in chunk.indices) {
            val candidate = chunk[index]
            if (candidate.endMs <= windowStart) continue
            val energy = AudioMath.rms(candidate.samples)
            if (energy <= lowestEnergy) {
                lowestEnergy = energy
                cutIndex = index
            }
        }

        val cutEnd = chunk[cutIndex].endMs
        val emittedFrames = chunk.subList(0, cutIndex + 1).toList()
        val overlapStart = cutEnd - OVERLAP_MS
        val overlapTail = tailFrom(overlapStart, cutIndex)
        val afterCut = chunk.subList(cutIndex + 1, chunk.size).toList()
        val remaining = (overlapTail + afterCut).toMutableList()

        val emitted = buildChunk(emittedFrames, ChunkReason.MAX_LENGTH, pendingOverlapMs)
        chunk = remaining
        pendingOverlapMs = OVERLAP_MS.toInt()
        speechMsInChunk = remaining.sumOf { if (isAudible(AudioMath.rms(it.samples))) it.endMs - it.startMs else 0L }
        if (remaining.isEmpty()) {
            active = false
            pendingOverlapMs = 0
            silenceMs = 0L
            speechMsInChunk = 0L
        }
        return emitted
    }

    private fun tailFrom(fromMs: Long, lastIndex: Int): MutableList<BufferedFrame> {
        val result = mutableListOf<BufferedFrame>()
        for (index in 0..lastIndex) {
            val frame = chunk[index]
            if (frame.endMs <= fromMs) continue
            if (frame.startMs >= fromMs) {
                result.add(frame)
                continue
            }
            val skip = ((fromMs - frame.startMs) * SAMPLE_RATE / 1000L).toInt()
                .coerceIn(0, frame.samples.size)
            if (skip < frame.samples.size) {
                val trimmed = frame.samples.copyOfRange(skip, frame.samples.size)
                val start = frame.startMs + skip * 1000L / SAMPLE_RATE
                result.add(BufferedFrame(trimmed, start, frame.endMs))
            }
        }
        return result
    }

    private fun buildChunk(
        frames: List<BufferedFrame>,
        reason: ChunkReason,
        overlapMs: Int,
    ): AudioChunk {
        var sampleCount = 0
        for (frame in frames) sampleCount += frame.samples.size
        val samples = FloatArray(sampleCount)
        var offset = 0
        for (frame in frames) {
            val converted = AudioMath.shortToFloat(frame.samples)
            converted.copyInto(samples, offset)
            offset += converted.size
        }
        return AudioChunk(
            index = nextIndex++,
            samples = samples,
            sampleRate = SAMPLE_RATE,
            startMs = frames.first().startMs,
            endMs = frames.last().endMs,
            overlapMs = overlapMs,
            reason = reason,
        )
    }

    override fun flush(capturedAtMs: Long): AudioChunk? {
        if (sessionCapped || !active || chunk.isEmpty()) {
            clearActiveState()
            return null
        }
        val result = buildChunk(chunk.toList(), ChunkReason.FLUSH, pendingOverlapMs)
        clearActiveState()
        return result
    }

    override fun reset() {
        preRoll.clear()
        clearActiveState()
        nextIndex = 0
        sessionStartMs = null
        sessionCapped = false
        totalSpeechMs = 0L
    }

    private fun appendPreRoll(frame: BufferedFrame) {
        preRoll.addLast(frame)
        while (preRoll.size > 1 && preRoll.last().endMs - preRoll.first().startMs > PRE_ROLL_MS) {
            preRoll.removeFirst()
        }
    }

    private fun retainPreRoll(frames: List<BufferedFrame>) {
        preRoll.clear()
        if (frames.isEmpty()) return
        val cutoff = frames.last().endMs - PRE_ROLL_MS
        for (frame in frames) {
            if (frame.endMs <= cutoff) continue
            if (frame.startMs >= cutoff) {
                preRoll.addLast(frame)
            } else {
                val skip = ((cutoff - frame.startMs) * SAMPLE_RATE / 1000L).toInt()
                    .coerceIn(0, frame.samples.size)
                if (skip < frame.samples.size) {
                    val trimmed = frame.samples.copyOfRange(skip, frame.samples.size)
                    preRoll.addLast(
                        BufferedFrame(
                            trimmed,
                            frame.startMs + skip * 1000L / SAMPLE_RATE,
                            frame.endMs,
                        ),
                    )
                }
            }
        }
    }

    private fun clearActiveState() {
        chunk.clear()
        active = false
        speechMsInChunk = 0L
        silenceMs = 0L
        speechFloorRms = MIN_AUDIBLE_RMS
        pendingOverlapMs = 0
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_MS = 20
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
        const val PRE_ROLL_MS = 300L
        const val MIN_SPEECH_MS = 250L
        const val SILENCE_FINALIZE_MS = 500L
        const val MAX_CHUNK_MS = 25_000L
        const val OVERLAP_MS = 250L
        const val MAX_CUT_SEARCH_MS = 2_000L
        const val SESSION_CAP_MS = 10 * 60 * 1000L
        private const val MIN_AUDIBLE_RMS = 0.0001
    }
}
