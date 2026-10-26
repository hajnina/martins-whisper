package com.martins.whisper.service

import com.martins.whisper.core.AudioChunk
import com.martins.whisper.core.ChunkReason
import com.martins.whisper.core.ModelState
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.Transcript
import com.martins.whisper.core.asr.ModelManager
import kotlinx.coroutines.runBlocking

/**
 * Owns the engine lifecycle for [DictationService] and the audio captured while it loads.
 *
 * - [ModelManager] provides the thread-safe lazy load, switch and 300 s idle unload; the clock is
 *   injected so tests can move time without sleeping.
 * - Capture may start before [load] finishes: frames go into [AudioCaptureBuffer] and are drained
 *   in order by [finishSession], so the first syllables are not lost.
 *
 * No Android types are used, which keeps this class host-JVM testable.
 */
class DictationEngineHost(
    private val createEngine: () -> SpeechEngine,
    private val clock: () -> Long,
    val idleMs: Long = ModelManager.DEFAULT_IDLE_MS,
    private val maxFrames: Int = AudioCaptureBuffer.DEFAULT_MAX_FRAMES,
) {

    private val manager = ModelManager(
        loader = { createEngine() },
        closer = { engine -> engine.close() },
        clock = clock,
        idleMs = idleMs,
    )
    private val buffer = AudioCaptureBuffer(maxFrames)

    /** Id of the model the current loader was installed for; null until [prepare]. */
    @Volatile
    var modelId: String? = null
        private set

    val state: ModelState
        get() = manager.state

    /** Installs [loader] when the selected model changed; unloads the previous engine immediately. */
    fun prepare(modelId: String) {
        if (this.modelId == modelId) return
        manager.switchModel { createEngine() }
        this.modelId = modelId
    }

    fun beginSession() {
        buffer.clear()
    }

    fun addFrame(frame: ShortArray): Boolean = buffer.add(frame)

    fun bufferedFrames(): Int = buffer.frameCount()

    fun bufferedSamples(): Long = buffer.sampleCount()

    /** Loads the model. Safe to call on a background dispatcher; retries after a failed load. */
    fun load(): SpeechEngine = manager.getOrLoad()

    /** Drains the buffered capture. Returns null when nothing was captured. */
    fun finishSession(): CapturedAudio? {
        val frames = buffer.snapshot()
        buffer.clear()
        return frames.takeIf { it.isNotEmpty() }?.let { CapturedAudio(it) }
    }

    fun cancelSession() {
        buffer.clear()
    }

    /**
     * Runs one transcription through the manager lock. Blocking by design: the service calls this
     * on [kotlinx.coroutines.Dispatchers.Default].
     */
    fun transcribe(samples: FloatArray, durationMs: Long, language: String?): Transcript =
        manager.withModel { engine ->
            runBlocking {
                engine.transcribe(
                    AudioChunk(
                        index = 0,
                        samples = samples,
                        startMs = 0L,
                        endMs = durationMs,
                        reason = ChunkReason.FLUSH,
                    ),
                    language,
                )
            }
        }

    /** Watchdog hook: frees the engine when it has been idle for [idleMs]. */
    fun unloadIfIdle(): Boolean = manager.maybeUnload()

    fun close() {
        buffer.clear()
        manager.close()
    }
}
