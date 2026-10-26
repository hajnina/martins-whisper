package com.martins.whisper.service

/**
 * Ordered, thread-safe capture buffer filled from the audio thread while the engine is still
 * loading. Frames are never discarded by [add]: the capacity is sized for a full hold, and a
 * false return means the caller must abort the session instead of silently losing speech.
 */
class AudioCaptureBuffer(private val maxFrames: Int = DEFAULT_MAX_FRAMES) {

    init {
        require(maxFrames > 0) { "maxFrames must be positive" }
    }

    private val frames = ArrayList<ShortArray>(minOf(maxFrames, 4096))
    private var sampleCount: Long = 0L

    @Synchronized
    fun add(frame: ShortArray): Boolean {
        if (frames.size >= maxFrames) return false
        frames.add(frame)
        sampleCount += frame.size
        return true
    }

    @Synchronized
    fun frameCount(): Int = frames.size

    @Synchronized
    fun sampleCount(): Long = sampleCount

    @Synchronized
    fun isFull(): Boolean = frames.size >= maxFrames

    @Synchronized
    fun snapshot(): List<ShortArray> = frames.toList()

    @Synchronized
    fun clear() {
        frames.clear()
        sampleCount = 0L
    }

    companion object {
        /** 20 ms at 16 kHz, matching [com.martins.whisper.audio.AudioRecordSource]. */
        const val FRAME_SAMPLES: Int = 320
        const val FRAME_MS: Long = 20L

        /** Capacity for a 600 s hold plus a small scheduler margin. */
        const val DEFAULT_MAX_FRAMES: Int = 30_000 + 512
    }
}

/** Immutable audio captured for one dictation session, still in frame order. */
class CapturedAudio(val frames: List<ShortArray>, private val sampleRate: Int = 16_000) {

    val sampleCount: Long = frames.sumOf { it.size.toLong() }

    fun durationMs(): Long = sampleCount * 1000L / sampleRate

    fun toFloatArray(): FloatArray {
        val samples = FloatArray(sampleCount.toInt())
        var write = 0
        for (frame in frames) {
            for (sample in frame) {
                if (write >= samples.size) break
                samples[write++] = sample / 32768f
            }
        }
        return samples
    }
}
