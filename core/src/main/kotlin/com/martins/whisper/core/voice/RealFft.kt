package com.martins.whisper.core.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Minimal in-place radix-2 FFT for a power-of-two real frame; returns a one-sided magnitude spectrum. */
internal class RealFft(val size: Int) {
    init {
        require(size >= 16 && size and (size - 1) == 0) { "size must be a power of two >= 16" }
    }

    private val half = size / 2
    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val cosine = FloatArray(half) { cos(-2.0 * PI * it / size).toFloat() }
    private val sine = FloatArray(half) { sin(-2.0 * PI * it / size).toFloat() }

    /**
     * Writes magnitudes for bins 1..size/2-1 into [out]; DC and Nyquist are zeroed.
     * [out] must be at least [size]/2 long.
     */
    fun magnitude(samples: FloatArray, window: FloatArray, out: FloatArray) {
        require(samples.size >= size && window.size >= size && out.size >= half)
        for (i in 0 until size) {
            re[i] = samples[i] * window[i]
            im[i] = 0f
        }
        transform()
        out[0] = 0f
        for (bin in 1 until half) {
            out[bin] = sqrt(re[bin] * re[bin] + im[bin] * im[bin])
        }
    }

    private fun transform() {
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }
        var span = 2
        while (span <= size) {
            val halfSpan = span / 2
            val step = size / span
            var start = 0
            while (start < size) {
                for (k in 0 until halfSpan) {
                    val cosValue = cosine[k * step]
                    val sinValue = sine[k * step]
                    val even = start + k
                    val odd = even + halfSpan
                    val tre = re[odd] * cosValue - im[odd] * sinValue
                    val tim = re[odd] * sinValue + im[odd] * cosValue
                    re[odd] = re[even] - tre
                    im[odd] = im[even] - tim
                    re[even] += tre
                    im[even] += tim
                }
                start += span
            }
            span = span shl 1
        }
    }
}
