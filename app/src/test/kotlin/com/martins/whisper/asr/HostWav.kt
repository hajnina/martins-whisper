package com.martins.whisper.asr

import java.io.File

/** Shared host-tracer helper: decode 16 kHz mono PCM16 WAV into float samples. */
object HostWav {

    fun read16kMono(file: File): FloatArray {
        val bytes = file.readBytes()
        require(bytes.size > 44) { "not a WAV file: $file" }
        fun ascii(offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
        require(ascii(0) == "RIFF" && ascii(8) == "WAVE") { "not a RIFF/WAVE file: $file" }

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
            require(size >= 0 && start + size <= bytes.size) { "truncated WAV chunk $id" }
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

        require(format == 1 && channels == 1 && rate == 16_000 && bits == 16) {
            "expected 16 kHz mono PCM16, got format=$format channels=$channels rate=$rate bits=$bits ($file)"
        }
        val data = requireNotNull(pcm) { "WAV has no data chunk ($file)" }
        return FloatArray(data.size / 2) { index ->
            le16(data, index * 2).toShort() / 32768f
        }
    }

    fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
