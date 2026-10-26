package com.martins.whisper.core

import com.martins.whisper.core.audio.AudioChunker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AudioChunkerTest {
    private fun frame(amplitude: Int): ShortArray = ShortArray(320) { amplitude.toShort() }

    private fun feed(
        chunker: AudioChunker,
        durationMs: Int,
        amplitude: Int,
        startMs: Long,
        output: MutableList<AudioChunk> = mutableListOf(),
    ): Long {
        var time = startMs
        repeat(durationMs / AudioChunker.FRAME_MS) {
            output += chunker.push(frame(amplitude), time)
            time += AudioChunker.FRAME_MS
        }
        return time
    }

    @Test
    fun `first word includes pre roll before onset`() {
        val chunker = AudioChunker()
        val chunks = mutableListOf<AudioChunk>()
        var time = feed(chunker, 300, 0, 0, chunks)
        time = feed(chunker, 300, 2_000, time, chunks)
        feed(chunker, 600, 0, time, chunks)

        assertEquals(1, chunks.size)
        val chunk = chunks.single()
        assertEquals(0, chunk.startMs)
        assertEquals(ChunkReason.SILENCE, chunk.reason)
        assertTrue(chunk.endMs > 600)
        assertEquals(0, chunk.index)
    }

    @Test
    fun `short blips are ignored`() {
        val chunker = AudioChunker()
        val chunks = mutableListOf<AudioChunk>()
        var time = feed(chunker, 100, 2_000, 0, chunks)
        feed(chunker, 900, 0, time, chunks)

        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `silence finalizes speech chunk`() {
        val chunker = AudioChunker()
        val chunks = mutableListOf<AudioChunk>()
        var time = feed(chunker, 300, 2_000, 0, chunks)
        feed(chunker, 600, 0, time, chunks)

        assertEquals(1, chunks.size)
        assertEquals(ChunkReason.SILENCE, chunks[0].reason)
        assertTrue(chunks[0].endMs - chunks[0].startMs >= 800)
        assertTrue(chunker.speechMs >= 300)
    }

    @Test
    fun `maximum length split has 250ms overlap`() {
        val chunker = AudioChunker()
        val chunks = mutableListOf<AudioChunk>()
        var time = feed(chunker, 26_000, 2_000, 0, chunks)
        feed(chunker, 600, 0, time, chunks)

        assertTrue(chunks.size >= 2)
        val first = chunks.first { it.reason == ChunkReason.MAX_LENGTH }
        val next = chunks[chunks.indexOf(first) + 1]
        assertEquals(25_000, first.endMs - first.startMs)
        assertEquals(0, first.overlapMs)
        assertEquals(250, next.overlapMs)
        assertEquals(first.endMs - 250, next.startMs)
    }

    @Test
    fun `flush emits pending speech`() {
        val chunker = AudioChunker()
        val chunks = mutableListOf<AudioChunk>()
        val time = feed(chunker, 300, 2_000, 0, chunks)
        val flushed = chunker.flush(time)

        assertNotNull(flushed)
        assertEquals(ChunkReason.FLUSH, flushed.reason)
        assertTrue(flushed.samples.isNotEmpty())
        assertEquals(null, chunker.flush(time + 20))
    }

    @Test
    fun `session cap stops chunk production`() {
        val chunker = AudioChunker()
        val chunks = mutableListOf<AudioChunk>()
        feed(chunker, 10 * 60 * 1000 + 2_000, 2_000, 0, chunks)

        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.endMs <= 10 * 60 * 1000 })
        assertEquals(ChunkReason.FLUSH, chunks.last().reason)
        assertTrue(chunker.push(frame(2_000), 10 * 60 * 1000 + 3_000).isEmpty())
    }

    @Test
    fun `reset starts a fresh session`() {
        val chunker = AudioChunker()
        val chunks = mutableListOf<AudioChunk>()
        val time = feed(chunker, 300, 2_000, 0, chunks)
        chunker.flush(time)
        chunker.reset()

        val afterReset = feed(chunker, 300, 2_000, 0)
        val flushed = chunker.flush(afterReset)
        assertEquals(0, flushed?.index)
        assertEquals(300, chunker.speechMs)
    }
}
