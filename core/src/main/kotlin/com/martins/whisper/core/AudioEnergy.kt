package com.martins.whisper.core

import kotlin.math.abs
import kotlin.math.sqrt

/** Level of captured, normalized (-1..1) audio: the peak sample and the RMS. */
data class AudioEnergy(val peak: Float, val rms: Float) {

    val isDigitalSilence: Boolean
        get() = peak <= 0f

    companion object {
        fun of(samples: FloatArray): AudioEnergy {
            if (samples.isEmpty()) return AudioEnergy(0f, 0f)
            var peak = 0f
            var sumSquares = 0.0
            for (sample in samples) {
                val magnitude = abs(sample)
                if (magnitude > peak) peak = magnitude
                sumSquares += sample.toDouble() * sample
            }
            return AudioEnergy(peak, sqrt(sumSquares / samples.size).toFloat())
        }
    }
}
