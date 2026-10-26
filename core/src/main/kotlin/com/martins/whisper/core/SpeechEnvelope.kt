package com.martins.whisper.core

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Level envelope of a captured buffer with the relative gate the device verdict is built on.
 * The gate compares the loudest speech window against the measured noise floor (a low
 * percentile of window RMS), not against one absolute level, because mic gain differs per
 * phone; an absolute floor cap keeps speech that fills the whole buffer detectable.
 */
data class SpeechEnvelope(
    val peakDb: Float,
    val speechRmsDb: Float,
    val floorDb: Float,
) {
    /** Set analytically when of() capped the floor; marks uniformly loud buffers. */
    var floorRuleApplied: Boolean = false
        internal set

    /** True when even the quietest window is above sensible voice level: continuous speech. */
    val uniformlyLoud: Boolean
        get() = floorRuleApplied && floorDb >= ABSOLUTE_FLOOR_DB

    val marginDb: Float
        get() = speechRmsDb - floorDb

    val isSpeech: Boolean
        get() = peakDb > LOWEST_DB && (uniformlyLoud || marginDb >= GATE_MARGIN_DB)

    fun gateMessage(): String =
        "Didn't catch that (peak %s dBFS, floor %s dBFS, gate floor+%d dB)"
            .format(fmt(peakDb), fmt(floorDb), GATE_MARGIN_DB.toInt())

    fun gateLog(): String =
        "peak %s dBFS, floor %s dBFS, margin %s dB, gate floor+%d dB"
            .format(fmt(peakDb), fmt(floorDb), fmt(marginDb), GATE_MARGIN_DB.toInt())

    companion object {
        const val GATE_MARGIN_DB: Float = 10f

        /** Window RMS the floor cannot rise above; speech almost always sits above it. */
        const val ABSOLUTE_FLOOR_DB: Float = -45f

        /** dBFS of a linear amplitude; 0 and below maps to [LOWEST_DB]. */
        const val LOWEST_DB: Float = -96f

        const val DEFAULT_WINDOW_SAMPLES: Int = 1600 // 100 ms at 16 kHz

        fun toDbfs(linear: Float): Float =
            if (linear <= 0f) LOWEST_DB else 20f * log10(linear)

        fun of(samples: FloatArray, windowSamples: Int = DEFAULT_WINDOW_SAMPLES): SpeechEnvelope {
            if (samples.isEmpty()) {
                return SpeechEnvelope(LOWEST_DB, LOWEST_DB, LOWEST_DB)
            }
            var peak = 0f
            for (sample in samples) {
                val magnitude = abs(sample)
                if (magnitude > peak) peak = magnitude
            }
            val windowRmsDb = rmsWindows(samples, windowSamples).sorted()
            val floor10 = windowRmsDb[windowRmsDb.size / 10]
            val capped = floor10 >= ABSOLUTE_FLOOR_DB
            val floorDb = minOf(floor10, ABSOLUTE_FLOOR_DB)
            val envelope = SpeechEnvelope(toDbfs(peak), windowRmsDb.last(), floorDb)
            if (capped) envelope.floorRuleApplied = true
            return envelope
        }
    }
}

fun fmt(value: Float): String = "%.0f".format(value)

// ---- helpers --------------------------------------------------------------

private fun rmsWindows(samples: FloatArray, windowSamples: Int): List<Float> {
    require(windowSamples > 0) { "windowSamples must be positive" }
    val windows = mutableListOf<Float>()
    var index = 0
    while (index < samples.size) {
        val end = minOf(index + windowSamples, samples.size)
        var sumSquares = 0.0
        for (i in index until end) {
            sumSquares += samples[i].toDouble() * samples[i]
        }
        val size = (end - index).toFloat()
        windows.add(SpeechEnvelope.toDbfs(sqrt(sumSquares / size).toFloat()))
        index = end
    }
    return windows
}
