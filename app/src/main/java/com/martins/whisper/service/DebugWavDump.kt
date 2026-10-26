package com.martins.whisper.service

import java.io.File
import java.io.FileOutputStream

/** Debug-only dump of a captured dictation buffer for host replay (owner/HUMAN-TEST-PROTOCOL.md). */
object DebugWavDump {

    const val SAMPLE_RATE: Int = 16_000

    fun dir(contextFilesDir: File): File = File(contextFilesDir, "debug-audio")

    /**
     * Writes a 16 kHz mono PCM16 WAV plus the transcript sidecar (.txt). Returns the WAV file.
     */
    fun dump(contextFilesDir: File, timestampMs: Long, samples: FloatArray, transcript: String): File {
        val dir = dir(contextFilesDir).apply { mkdirs() }
        val wav = File(dir, "$timestampMs.wav")
        val pcm = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val value = samples[i].coerceIn(-1f, 1f)
            val bits = (value * 32767f).toInt().toShort()
            pcm[i * 2] = (bits.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = (bits.toInt() shr 8).toByte()
        }
        FileOutputStream(wav).use { out ->
            val dataLen = pcm.size
            out.write(byteArrayOf(0x52, 0x49, 0x46, 0x46)) // RIFF
            out.writeLe32(36 + dataLen)
            out.write(byteArrayOf(0x57, 0x41, 0x56, 0x45)) // WAVE
            out.write(byteArrayOf(0x66, 0x6D, 0x74, 0x20)) // fmt
            out.writeLe32(16)
            out.writeLe16(1)
            out.writeLe16(1)
            out.writeLe32(SAMPLE_RATE)
            out.writeLe32(SAMPLE_RATE * 2)
            out.writeLe16(2)
            out.writeLe16(16)
            out.write(byteArrayOf(0x64, 0x61, 0x74, 0x61)) // data
            out.writeLe32(dataLen)
            out.write(pcm)
        }
        File(dir, "$timestampMs.txt").writeText(transcript)
        return wav
    }
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
