package com.martins.whisper.debug

import java.io.File
import java.io.FileOutputStream

/** Minimal strict WAV reader/writer for the 16 kHz mono PCM16 fixtures used by device E2E. */
object WavPcm {

    const val SAMPLE_RATE: Int = 16_000

    data class Wav(val samples: ShortArray, val sampleRate: Int = SAMPLE_RATE)

    fun read(file: File): Wav {
        val bytes = file.readBytes()
        require(bytes.size >= 44) { "not a WAV file: ${file.name}" }
        fun ascii(offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
        require(ascii(0) == "RIFF" && ascii(8) == "WAVE") { "not a RIFF/WAVE file: ${file.name}" }

        var format = 0
        var channels = 0
        var rate = 0
        var bits = 0
        var pcm: ByteArray? = null
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = ascii(pos)
            val size = le32(bytes, pos + 4)
            val start = pos + 8
            require(size >= 0 && start + size <= bytes.size) {
                "truncated WAV chunk $id in ${file.name}"
            }
            when (id) {
                "fmt " -> {
                    format = le16(bytes, start)
                    channels = le16(bytes, start + 2)
                    rate = le32(bytes, start + 4)
                    bits = le16(bytes, start + 14)
                }
                "data" -> pcm = bytes.copyOfRange(start, start + size)
            }
            pos = start + size + (size and 1)
        }
        require(format == 1) { "expected PCM WAV, got format=$format (${file.name})" }
        require(channels == 1) { "expected mono WAV, got $channels channels (${file.name})" }
        require(rate == SAMPLE_RATE) { "expected ${SAMPLE_RATE} Hz WAV, got $rate (${file.name})" }
        require(bits == 16) { "expected 16-bit WAV, got $bits (${file.name})" }
        val data = requireNotNull(pcm) { "WAV has no data chunk: ${file.name}" }
        return Wav(ShortArray(data.size / 2) { index -> le16(data, index * 2).toShort() })
    }

    fun write(file: File, samples: ShortArray, sampleRate: Int = SAMPLE_RATE) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            val dataLen = samples.size * 2
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            out.writeLe32(36 + dataLen)
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.writeLe32(16)
            out.writeLe16(1)
            out.writeLe16(1)
            out.writeLe32(sampleRate)
            out.writeLe32(sampleRate * 2)
            out.writeLe16(2)
            out.writeLe16(16)
            out.write("data".toByteArray(Charsets.US_ASCII))
            out.writeLe32(dataLen)
            for (sample in samples) {
                out.write(sample.toInt() and 0xFF)
                out.write(sample.toInt() shr 8 and 0xFF)
            }
        }
    }

    fun frames(samples: ShortArray, frameSize: Int = 320): List<ShortArray> {
        require(frameSize > 0) { "frameSize must be positive" }
        val frames = ArrayList<ShortArray>(samples.size / frameSize + 1)
        var start = 0
        while (start < samples.size) {
            val end = minOf(start + frameSize, samples.size)
            frames += samples.copyOfRange(start, end)
            start = end
        }
        return frames
    }

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}

private fun FileOutputStream.writeLe16(value: Int) {
    write(value and 0xFF)
    write(value shr 8 and 0xFF)
}

private fun FileOutputStream.writeLe32(value: Int) {
    write(value and 0xFF)
    write(value shr 8 and 0xFF)
    write(value shr 16 and 0xFF)
    write(value shr 24 and 0xFF)
}
