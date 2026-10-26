package com.martins.whisper.debug

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WavPcmTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun tone(count: Int, amplitude: Int = 4_000): ShortArray =
        ShortArray(count) { index -> (amplitude * if (index % 2 == 0) 1 else -1).toShort() }

    @Test
    fun `write then read preserves every sample`() {
        val samples = tone(1_234)
        val file = File(tmp.root, "roundtrip.wav")
        WavPcm.write(file, samples)

        val read = WavPcm.read(file)

        assertArrayEquals(samples, read.samples)
        assertEquals(WavPcm.SAMPLE_RATE, read.sampleRate)
    }

    @Test
    fun `frame split keeps total samples and a short final frame`() {
        val samples = tone(641)
        val frames = WavPcm.frames(samples, 320)

        assertEquals(3, frames.size)
        assertEquals(listOf(320, 320, 1), frames.map { it.size })
        assertEquals(samples.size, frames.sumOf { it.size })
        assertArrayEquals(samples.copyOfRange(640, 641), frames[2])
    }

    @Test
    fun `a reader that drops the final sample is caught`() {
        val samples = tone(641)
        val file = File(tmp.root, "truncated-source.wav")
        WavPcm.write(file, samples)
        val read = WavPcm.read(file).samples.copyOf(samples.size - 1)

        assertEquals(
            "mutation check: dropping one sample must change the frame count",
            2,
            WavPcm.frames(read, 320).size,
        )
    }

    @Test
    fun `rejects WAVs that are not 16 kHz mono PCM16`() {
        val file = File(tmp.root, "wrong-rate.wav")
        WavPcm.write(file, tone(160), sampleRate = 8_000)
        assertThrows(IllegalArgumentException::class.java) { WavPcm.read(file) }
    }
}
