package com.martins.whisper.core.audio

import kotlin.math.sqrt

object AudioMath {
    fun rms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in samples) {
            val value = sample / Short.MAX_VALUE.toDouble()
            sum += value * value
        }
        return sqrt(sum / samples.size)
    }

    fun rmsFloat(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in samples) {
            val value = sample.toDouble()
            sum += value * value
        }
        return sqrt(sum / samples.size)
    }

    fun shortToFloat(samples: ShortArray): FloatArray =
        FloatArray(samples.size) { samples[it] / Short.MAX_VALUE.toFloat() }

    fun floatToShort(samples: FloatArray): ShortArray =
        ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
}
