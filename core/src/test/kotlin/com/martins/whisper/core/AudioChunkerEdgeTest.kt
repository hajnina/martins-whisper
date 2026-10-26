package com.martins.whisper.core

import com.martins.whisper.core.audio.AudioChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class AudioChunkerEdgeTest {

    private fun frame(amplitude: Int, size: Int = AudioChunker.FRAME_SAMPLES): ShortArray =
        ShortArray(size) { amplitude.toShort() }

    private fun feed(
        chunker: AudioChunker,
        count: Int,
        amplitude: Int,
        startMs: Long,
        out: MutableList<AudioChunk>,
        stepMs: Long = AudioChunker.FRAME_MS.toLong(),
    ): Long {
        var time = startMs
        repeat(count) {
            out += chunker.push(frame(amplitude), time)
            time += stepMs
        }
        return time
    }

    @Test
    fun `empty frames are ignored and do not start the session`() {
        val chunker = AudioChunker()
        assertTrue(chunker.push(ShortArray(0), 10_800_000L).isEmpty())
        assertEquals(0L, chunker.speechMs)
        assertNull(chunker.flush(10_800_000L))

        val out = mutableListOf<AudioChunk>()
        var time = feed(chunker, 15, 2_000, 0L, out)
        out += chunker.push(ShortArray(0), time)
        time += AudioChunker.FRAME_MS
        feed(chunker, 30, 0, time, out)

        assertEquals(1, out.size)
        assertEquals(0L, out.single().startMs)
        assertTrue(out.single().endMs >= 600L)
    }

    @Test
    fun `empty frames alone never emit or flush`() {
        val chunker = AudioChunker()
        repeat(50) {
            assertTrue(chunker.push(ShortArray(0), it * AudioChunker.FRAME_MS.toLong()).isEmpty())
        }
        assertNull(chunker.flush(1_000L))
        assertEquals(0L, chunker.speechMs)
    }

    @Test
    fun `exactly maximum length splits at twenty five seconds with overlap`() {
        val chunker = AudioChunker()
        val out = mutableListOf<AudioChunk>()
        val end = feed(chunker, 1_250, 2_000, 0L, out)

        assertEquals(1, out.size)
        val max = out.single()
        assertEquals(ChunkReason.MAX_LENGTH, max.reason)
        assertEquals(0L, max.startMs)
        assertEquals(25_000L, max.endMs)
        assertEquals(25_000L, max.endMs - max.startMs)
        assertEquals(0, max.overlapMs)
        assertEquals(400_000, max.samples.size)
        assertEquals(0, max.index)

        val next = chunker.flush(end)
        assertNotNull(next)
        assertEquals(ChunkReason.FLUSH, next!!.reason)
        assertEquals(1, next.index)
        assertEquals(24_750L, next.startMs)
        assertEquals(25_000L, next.endMs)
        assertEquals(250, next.overlapMs)
        assertEquals(4_000, next.samples.size)
        assertEquals(next.startMs, max.endMs - next.overlapMs)
    }

    @Test
    fun `one frame over maximum length leaves a flushed remainder`() {
        val chunker = AudioChunker()
        val out = mutableListOf<AudioChunk>()
        val end = feed(chunker, 1_251, 2_000, 0L, out)

        assertEquals(1, out.size)
        val max = out.single()
        assertEquals(ChunkReason.MAX_LENGTH, max.reason)
        assertEquals(25_000L, max.endMs - max.startMs)

        val next = chunker.flush(end)
        assertNotNull(next)
        assertEquals(ChunkReason.FLUSH, next!!.reason)
        assertEquals(24_750L, next.startMs)
        assertEquals(25_020L, next.endMs)
        assertEquals(270L, next.endMs - next.startMs)
        assertEquals(4_320, next.samples.size)
        assertEquals(250, next.overlapMs)
    }

    @Test
    fun `maximum split cuts at the lowest energy frame near the boundary`() {
        val chunker = AudioChunker()
        val out = mutableListOf<AudioChunk>()
        var time = 0L
        repeat(1_225) {
            out += chunker.push(frame(2_000), time)
            time += AudioChunker.FRAME_MS
        }
        out += chunker.push(frame(50), time)
        time += AudioChunker.FRAME_MS
        repeat(24) {
            out += chunker.push(frame(2_000), time)
            time += AudioChunker.FRAME_MS
        }

        assertEquals(1, out.size)
        val max = out.single()
        assertEquals(ChunkReason.MAX_LENGTH, max.reason)
        assertEquals(24_520L, max.endMs)
        assertEquals(24_520L, max.endMs - max.startMs)

        val next = chunker.flush(time)
        assertNotNull(next)
        assertEquals(24_270L, next!!.startMs)
        assertEquals(25_000L, next.endMs)
        assertEquals(250, next.overlapMs)
        assertEquals(730L, next.endMs - next.startMs)
        assertTrue(next.samples.size >= 11_680)
        assertEquals(0, next.samples.size % 160)
    }

    @Test
    fun `six hundred seconds of speech holds at the session cap`() {
        val chunker = AudioChunker()
        val out = mutableListOf<AudioChunk>()
        feed(chunker, 30_000, 2_000, 0L, out)

        assertEquals(600_000L, chunker.speechMs)
        assertTrue(out.isNotEmpty())
        assertEquals((0 until out.size).toList(), out.map { it.index })
        assertTrue(out.all { it.endMs <= 600_000L })
        assertTrue(out.all { it.sampleRate == AudioChunker.SAMPLE_RATE })
        assertTrue(out.all { it.samples.isNotEmpty() })

        val finalChunks = chunker.push(frame(2_000), 600_000L)
        assertEquals(1, finalChunks.size)
        val last = finalChunks.single()
        assertEquals(ChunkReason.FLUSH, last.reason)
        assertEquals(out.size, last.index)
        assertTrue(last.endMs <= 600_000L)
        assertEquals(600_000L, chunker.speechMs)

        assertTrue(chunker.push(frame(2_000), 600_020L).isEmpty())
        assertTrue(chunker.push(frame(2_000), 10_800_000L).isEmpty())
        assertNull(chunker.flush(10_800_000L))
    }

    @Test
    fun `three hours of pushes after the cap are inert and do not overflow`() {
        val chunker = AudioChunker()
        val loud = frame(2_000)
        chunker.push(loud, 0L)
        assertEquals(1, chunker.push(loud, 600_000L).size)
        assertEquals(20L, chunker.speechMs)

        var time = 0L
        var pushes = 0
        while (time <= 10_800_000L) {
            assertTrue(chunker.push(loud, time).isEmpty())
            pushes++
            time += AudioChunker.FRAME_MS
        }
        assertEquals(540_001, pushes)
        assertEquals(20L, chunker.speechMs)
        assertNull(chunker.flush(10_800_000L))
    }

    @Test
    fun `reset after the cap starts a fresh indexed session`() {
        val chunker = AudioChunker()
        chunker.push(frame(2_000), 0L)
        assertEquals(1, chunker.push(frame(2_000), 600_000L).size)

        chunker.reset()
        val out = mutableListOf<AudioChunk>()
        val end = feed(chunker, 15, 2_000, 0L, out)
        val flushed = chunker.flush(end)

        assertNotNull(flushed)
        assertEquals(0, flushed!!.index)
        assertEquals(ChunkReason.FLUSH, flushed.reason)
        assertEquals(300L, chunker.speechMs)
    }

    @Test
    fun `brief silence between speech does not finalize a chunk`() {
        val chunker = AudioChunker()
        val out = mutableListOf<AudioChunk>()
        var time = feed(chunker, 15, 2_000, 0L, out)
        time = feed(chunker, 20, 0, time, out)
        time = feed(chunker, 15, 2_000, time, out)
        feed(chunker, 30, 0, time, out)

        assertEquals(1, out.size)
        val chunk = out.single()
        assertEquals(ChunkReason.SILENCE, chunk.reason)
        assertEquals(0L, chunk.startMs)
        assertEquals(1_500L, chunk.endMs - chunk.startMs)
        assertEquals(600L, chunker.speechMs)
    }

    @Test
    fun `arbitrary frame sizes preserve every sample`() {
        val chunker = AudioChunker()
        assertTrue(chunker.push(frame(2_000, 1_000), 0L).isEmpty())

        val flushed = chunker.flush(62L)
        assertNotNull(flushed)
        assertEquals(1_000, flushed!!.samples.size)
        assertEquals(0L, flushed.startMs)
        assertEquals(62L, flushed.endMs)
        assertEquals(ChunkReason.FLUSH, flushed.reason)
    }

    @Test
    fun `large absolute timestamps stay exact and cap relative to the session start`() {
        val chunker = AudioChunker()
        val start = 10_800_000L
        val out = mutableListOf<AudioChunk>()
        var time = feed(chunker, 15, 2_000, start, out)
        feed(chunker, 30, 0, time, out)

        assertEquals(1, out.size)
        assertEquals(start, out.single().startMs)
        assertEquals(300L, chunker.speechMs)
        assertTrue(chunker.push(frame(2_000), start + 600_000L).isEmpty())
        assertTrue(chunker.push(frame(2_000), start + 600_020L).isEmpty())
    }

    @Test
    fun `seeded random stream keeps index overlap and timestamp invariants`() {
        val chunker = AudioChunker()
        val random = Random(20261026L)
        val sizes = intArrayOf(320, 640, 960)
        val out = mutableListOf<AudioChunk>()
        var time = 0L
        repeat(900) {
            val size = sizes[random.nextInt(sizes.size)]
            val amplitude = if (random.nextInt(4) == 0) 0 else 1_000 + random.nextInt(2_001)
            out += chunker.push(frame(amplitude, size), time)
            time += size * 1_000L / AudioChunker.SAMPLE_RATE
        }
        chunker.flush(time)?.let { out += it }

        assertTrue(out.isNotEmpty())
        assertEquals((0 until out.size).toList(), out.map { it.index })
        var previous: AudioChunk? = null
        for (chunk in out) {
            assertEquals(AudioChunker.SAMPLE_RATE, chunk.sampleRate)
            assertTrue("chunk ${chunk.index} has no samples", chunk.samples.isNotEmpty())
            assertTrue(chunk.startMs >= 0L)
            assertTrue(chunk.endMs >= chunk.startMs)
            assertTrue(chunk.overlapMs == 0 || chunk.overlapMs == 250)
            val sampleMs = chunk.samples.size * 1_000L / AudioChunker.SAMPLE_RATE
            assertTrue(
                "chunk ${chunk.index} samples ${chunk.samples.size} shorter than duration ${chunk.endMs - chunk.startMs}",
                sampleMs >= (chunk.endMs - chunk.startMs) - 1L,
            )
            assertEquals(0, chunk.samples.size % 160)
            val prev = previous
            if (prev != null) {
                if (chunk.overlapMs > 0) {
                    assertEquals(prev.endMs - chunk.overlapMs, chunk.startMs)
                } else {
                    assertTrue(chunk.startMs >= prev.endMs)
                }
            }
            previous = chunk
        }
    }
}
