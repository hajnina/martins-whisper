package com.martins.whisper.harness

import com.martins.whisper.core.AudioChunk
import com.martins.whisper.core.ChunkReason
import com.martins.whisper.core.ModelState
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.Transcript
import com.martins.whisper.core.TranscriptMerger
import com.martins.whisper.core.asr.FakeSpeechEngine
import com.martins.whisper.core.asr.ModelManager
import com.martins.whisper.core.audio.AudioChunker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.sin

/**
 * Layer 1 end-to-end: synthesized PCM -> EnergyVad/AudioChunker -> FakeSpeechEngine ->
 * TranscriptMerger -> MarkUsed/idle-unload lifecycle. No model files, no device.
 */
class FakeAudioPipelineE2ETest {

    private val tone = ShortArray(FRAME_SAMPLES) { index ->
        (6_000 * sin(2.0 * PI * 440.0 * index / SAMPLE_RATE)).toInt().toShort()
    }
    private val silence = ShortArray(FRAME_SAMPLES)

    private fun feed(
        chunker: AudioChunker,
        samples: ShortArray,
        durationMs: Int,
        startMs: Long,
        out: MutableList<AudioChunk>,
    ): Long {
        var time = startMs
        repeat(durationMs / FRAME_MS) {
            out += chunker.push(samples, time)
            time += FRAME_MS
        }
        return time
    }

    @Test
    fun `fake audio flows through vad chunker engine merger and idle unload`() {
        val chunker = AudioChunker()
        val chunks = mutableListOf<AudioChunk>()
        var time = feed(chunker, silence, 400, 0L, chunks)
        time = feed(chunker, tone, 1_200, time, chunks)
        time = feed(chunker, silence, 700, time, chunks)
        time = feed(chunker, tone, 26_000, time, chunks)
        feed(chunker, silence, 700, time, chunks)

        assertEquals(
            listOf(ChunkReason.SILENCE, ChunkReason.MAX_LENGTH, ChunkReason.SILENCE),
            chunks.map { it.reason },
        )
        assertEquals(listOf(0, 1, 2), chunks.map { it.index })
        assertEquals(0, chunks[0].overlapMs)
        assertEquals(0, chunks[1].overlapMs)
        assertEquals(AudioChunker.OVERLAP_MS.toInt(), chunks[2].overlapMs)
        assertEquals(
            chunks[1].endMs - AudioChunker.OVERLAP_MS,
            chunks[2].startMs,
        )
        assertTrue(chunker.speechMs >= 26_000)

        val texts = listOf("alpha beta", "gamma delta", "delta epsilon")
        val engine = FakeSpeechEngine(
            backend = "fake-e2e",
            respond = { chunk, language ->
                Transcript(text = texts[chunk.index], language = language ?: "en")
            },
        )
        val clock = AtomicLong(0)
        val manager = ModelManager<SpeechEngine>(
            loader = { engine },
            closer = { it.close() },
            clock = clock::get,
            idleMs = IDLE_UNLOAD_MS,
        )

        val parts = chunks.map { chunk ->
            manager.withModel { active ->
                runBlocking { active.transcribe(chunk, "en") }.text
            }
        }
        assertEquals(chunks.map { it.index }, engine.received.map { it.chunk.index })
        assertEquals(listOf("en", "en", "en"), engine.languages)

        val boundaries = chunks.zipWithNext { _, next -> next.overlapMs }
        val merged = TranscriptMerger.merge(parts, boundaries)
        assertEquals("alpha beta gamma delta epsilon", merged)
        assertEquals(ModelState.READY, manager.state)

        clock.set(IDLE_UNLOAD_MS - 1)
        assertFalse(manager.maybeUnload())
        clock.set(IDLE_UNLOAD_MS)
        assertTrue(manager.maybeUnload())
        assertTrue(engine.closed)
        assertEquals(1, engine.closeCalls)
        assertEquals(ModelState.UNLOADED, manager.state)
    }

    @Test
    fun `silence only produces no chunks and no engine calls`() {
        val chunker = AudioChunker()
        val chunks = mutableListOf<AudioChunk>()
        feed(chunker, silence, 3_000, 0L, chunks)

        assertTrue(chunks.isEmpty())
        assertEquals(0, chunker.speechMs)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_MS = 20
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
        const val IDLE_UNLOAD_MS = 300_000L
    }
}
