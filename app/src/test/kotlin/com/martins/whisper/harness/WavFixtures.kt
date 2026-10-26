package com.martins.whisper.harness

import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** WAV fixtures and level math for the host replay harness. */
object WavFixtures {

    const val SAMPLE_RATE: Int = 16_000

    data class Wav(val samples: ShortArray, val sampleRate: Int)

    fun read(file: File): Wav {
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
            require(size >= 0 && start + size <= bytes.size) { "truncated WAV chunk $id in $file" }
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

        require(format == 1 && channels == 1 && rate == SAMPLE_RATE && bits == 16) {
            "expected 16 kHz mono PCM16, got format=$format channels=$channels rate=$rate bits=$bits ($file)"
        }
        val data = requireNotNull(pcm) { "WAV has no data chunk: $file" }
        val samples = ShortArray(data.size / 2) { index -> le16(data, index * 2).toShort() }
        return Wav(samples, rate)
    }

    /**
     * Steady harmonic complex (120 Hz plus formant-ish harmonics) scaled to [rmsDbfs] overall RMS.
     * A steady envelope keeps the gate-flip level exact instead of depending on crest factor.
     */
    fun speechLike(seconds: Double, rmsDbfs: Double, sampleRate: Int = SAMPLE_RATE): ShortArray {
        val count = (seconds * sampleRate).roundToInt()
        require(count > 0) { "duration must be positive" }
        val harmonics = listOf(1.0 to 1.0, 2.0 to 0.5, 3.0 to 0.3, 4.0 to 0.15, 6.0 to 0.08)
        val raw = DoubleArray(count) { index ->
            val t = index.toDouble() / sampleRate
            harmonics.sumOf { (multiple, amplitude) ->
                amplitude * sin(2.0 * PI * 120.0 * multiple * t)
            }
        }
        val rawRms = sqrt(raw.sumOf { it * it } / count)
        val scale = dbfsToLinear(rmsDbfs) / rawRms
        return ShortArray(count) { index ->
            (raw[index] * scale * Short.MAX_VALUE)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    /**
     * Quiet floor with loud speech bursts (400 ms on / 400 ms off) — the shape of real dictation,
     * which the relative (floor + margin) gate exists to handle.
     */
    fun bursty(seconds: Double, floorDbfs: Double, burstDbfs: Double): ShortArray {
        val speech = speechLike(seconds, burstDbfs)
        val quiet = speechLike(seconds, floorDbfs)
        val out = speech.copyOf()
        // switch to quiet during the gaps of every second 400 ms half-beat
        val onMs = 400.0
        val period = 800.0
        val sr = SAMPLE_RATE
        val idx = 0 until out.size
        for (i in idx) {
            val phase = (i.toDouble() / sr * 1000.0) % period
            if (phase >= onMs) out[i] = quiet[i]
        }
        return out
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

    fun peakDbfs(samples: ShortArray): Double {
        var peak = 0.0
        for (sample in samples) {
            val value = abs(sample / 32768.0)
            if (value > peak) peak = value
        }
        return linearToDbfs(peak)
    }

    fun rmsDbfs(samples: ShortArray): Double {
        if (samples.isEmpty()) return linearToDbfs(0.0)
        var sum = 0.0
        for (sample in samples) {
            val value = sample / 32768.0
            sum += value * value
        }
        return linearToDbfs(sqrt(sum / samples.size))
    }

    fun rms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in samples) {
            val value = sample / 32768.0
            sum += value * value
        }
        return sqrt(sum / samples.size)
    }

    fun linearToDbfs(value: Double): Double =
        if (value <= 0.0) -120.0 else 20.0 * log10(value)

    fun dbfsToLinear(db: Double): Double = 10.0.pow(db / 20.0)

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
