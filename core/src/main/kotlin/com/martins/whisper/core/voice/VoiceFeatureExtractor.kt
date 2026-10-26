package com.martins.whisper.core.voice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Turns 16 kHz PCM frames (the capture thread already has them) into [VoiceFeatures] at the
 * frame rate: RMS level, YIN pitch with octave guards, FFT spectral centroid and spectral-flux
 * onset. Pure Kotlin, no Android dependencies, host-testable.
 *
 * Cost: one 1024-point FFT plus an autocorrelation over ~229 lags per 20 ms frame (well under
 * 1 ms on a modern phone). A rolling 64 ms window lets short capture frames still hold enough
 * periods for a deep voice.
 */
class VoiceFeatureExtractor(
    private val sampleRate: Int = 16_000,
    private val analysisSize: Int = 1024,
    private val minPitchHz: Float = 70f,
    private val maxPitchHz: Float = 500f,
) {
    init {
        require(analysisSize >= 256 && analysisSize and (analysisSize - 1) == 0) {
            "analysisSize must be a power of two >= 256"
        }
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(minPitchHz > 0f && maxPitchHz > minPitchHz) { "invalid pitch range" }
    }

    private val half = analysisSize / 2
    private val history = FloatArray(analysisSize)
    private var historyCount = 0
    private var writeIndex = 0

    private val minLag = max(2, (sampleRate / maxPitchHz).toInt())
    private val maxLag = min(analysisSize / 2, (sampleRate / minPitchHz).toInt()).coerceAtLeast(minLag + 1)
    private val windowLength = analysisSize - maxLag

    private val hann = FloatArray(analysisSize) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (analysisSize - 1))).toFloat()
    }
    private val fft = RealFft(analysisSize)
    private val diff = FloatArray(maxLag + 1)
    private val cmnd = FloatArray(maxLag + 1)
    private val magnitude = FloatArray(half)
    private var previousMagnitude: FloatArray? = null

    private var hasLevel = false
    private var smoothedLevel = 0f
    private var smoothedPeak = 0f
    private var smoothedBrightness = 0f
    private var smoothedPitch = 0f
    private var hasPitch = false
    private var pitchLogMin = ln(minPitchHz.toDouble())
    private var pitchLogMax = ln(maxPitchHz.toDouble())

    /** Clears all running state, e.g. when the speaker or the recording session changes. */
    @Synchronized
    fun reset() {
        history.fill(0f)
        historyCount = 0
        writeIndex = 0
        previousMagnitude = null
        hasLevel = false
        smoothedLevel = 0f
        smoothedPeak = 0f
        smoothedBrightness = 0f
        smoothedPitch = 0f
        hasPitch = false
        pitchLogMin = ln(minPitchHz.toDouble())
        pitchLogMax = ln(maxPitchHz.toDouble())
    }

    /** Convenience for capture buffers that are still shorts (-1..1 full scale). */
    @Synchronized
    fun process(frame: ShortArray, timestampMs: Long = 0L): VoiceFeatures =
        process(FloatArray(frame.size) { frame[it] / Short.MAX_VALUE.toFloat() }, timestampMs)

    /**
     * Analyses one capture frame. The frame may be shorter than the analysis window; the
     * extractor accumulates a rolling window and returns pitch/spectral values once enough
     * audio is available.
     */
    @Synchronized
    fun process(frame: FloatArray, timestampMs: Long = 0L): VoiceFeatures {
        val rms = rms(frame)
        val levelTarget = dbfsToUnit(rms)
        val peakTarget = dbfsToUnit(peak(frame))
        if (!hasLevel) {
            smoothedLevel = levelTarget
            smoothedPeak = peakTarget
            hasLevel = true
        } else {
            smoothedLevel += LEVEL_SMOOTHING * (levelTarget - smoothedLevel)
            smoothedPeak += LEVEL_SMOOTHING * (peakTarget - smoothedPeak)
        }

        append(frame)
        if (historyCount < analysisSize) {
            return VoiceFeatures(
                level = smoothedLevel,
                peakLevel = smoothedPeak,
                pitchHz = null,
                pitchNorm = 0f,
                voicedProb = 0f,
                brightness = smoothedBrightness,
                onset = false,
                timestampMs = timestampMs,
            )
        }

        val ordered = orderedHistory()
        fft.magnitude(ordered, hann, magnitude)
        val brightness = spectralBrightness()
        smoothedBrightness += BRIGHTNESS_SMOOTHING * (brightness - smoothedBrightness)
        val pitch = estimatePitch(ordered, rms)

        val pitchHz: Float?
        val voicedProb: Float
        val pitchNorm: Float
        if (pitch != null) {
            voicedProb = pitch.confidence
            smoothedPitch = if (hasPitch) {
                PITCH_SMOOTHING * smoothedPitch + (1f - PITCH_SMOOTHING) * pitch.hz
            } else {
                pitch.hz.also { hasPitch = true }
            }
            pitchHz = smoothedPitch
            pitchNorm = normalisePitch(smoothedPitch)
        } else {
            voicedProb = 0f
            hasPitch = false
            pitchHz = null
            pitchNorm = 0f
        }

        return VoiceFeatures(
            level = smoothedLevel,
            peakLevel = smoothedPeak,
            pitchHz = pitchHz,
            pitchNorm = pitchNorm,
            voicedProb = voicedProb,
            brightness = smoothedBrightness,
            onset = onsetDetected(rms),
            timestampMs = timestampMs,
        )
    }

    private fun append(frame: FloatArray) {
        for (sample in frame) {
            history[writeIndex] = sample
            writeIndex = (writeIndex + 1) % analysisSize
            if (historyCount < analysisSize) historyCount++
        }
    }

    private fun orderedHistory(): FloatArray {
        val out = FloatArray(analysisSize)
        val start = if (historyCount == analysisSize) writeIndex else 0
        for (i in 0 until analysisSize) out[i] = history[(start + i) % analysisSize]
        return out
    }

    private fun dbfsToUnit(amplitude: Float): Float {
        if (amplitude <= 1e-6f) return 0f
        return ((20f * log10(amplitude) + 60f) / 60f).coerceIn(0f, 1f)
    }

    private fun normalisePitch(hz: Float): Float {
        val logHz = ln(hz.toDouble())
        if (logHz < pitchLogMin) pitchLogMin = logHz
        if (logHz > pitchLogMax) pitchLogMax = logHz
        val span = pitchLogMax - pitchLogMin
        if (span <= 1e-6) return 0.5f
        return ((logHz - pitchLogMin) / span).coerceIn(0.0, 1.0).toFloat()
    }

    private class Pitch(val hz: Float, val confidence: Float)

    private fun estimatePitch(samples: FloatArray, rms: Float): Pitch? {
        if (rms < VOICING_RMS_FLOOR || windowLength <= minLag) return null

        diff[0] = 0f
        for (tau in 1..maxLag) {
            var sum = 0f
            var i = 0
            while (i < windowLength) {
                val delta = samples[i] - samples[i + tau]
                sum += delta * delta
                i++
            }
            diff[tau] = sum
        }

        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..maxLag) {
            running += diff[tau]
            cmnd[tau] = if (running <= 1e-12f) 1f else diff[tau] * tau / running
        }

        var chosen = -1
        var tau = minLag
        while (tau <= maxLag) {
            if (cmnd[tau] < YIN_THRESHOLD) {
                while (tau + 1 <= maxLag && cmnd[tau + 1] < cmnd[tau]) tau++
                chosen = tau
                break
            }
            tau++
        }

        val confidence: Float
        if (chosen < 0) {
            var best = minLag
            for (candidate in minLag..maxLag) if (cmnd[candidate] < cmnd[best]) best = candidate
            if (cmnd[best] > YIN_VOICING_CEILING) return null
            chosen = best
            confidence = (1f - cmnd[best]).coerceIn(0f, 1f)
        } else {
            confidence = (1f - cmnd[chosen]).coerceIn(0f, 1f)
        }

        var refined = chosen.toFloat()
        if (chosen in 1 until maxLag) {
            val a = cmnd[chosen - 1]
            val b = cmnd[chosen]
            val c = cmnd[chosen + 1]
            val denom = a - 2f * b + c
            if (abs(denom) > 1e-9f) refined += (0.5f * (a - c) / denom).coerceIn(-1f, 1f)
        }
        if (refined <= 0f) return null
        return Pitch((sampleRate / refined).coerceIn(minPitchHz, maxPitchHz), confidence)
    }

    private fun spectralBrightness(): Float {
        var weighted = 0.0
        var total = 0.0
        for (bin in 1 until half) {
            val value = magnitude[bin]
            weighted += value * (bin.toDouble() * sampleRate / analysisSize)
            total += value
        }
        if (total <= 1e-9) return 0f
        return ((weighted / total) / (sampleRate / 2.0)).toFloat().coerceIn(0f, 1f)
    }

    private fun onsetDetected(rms: Float): Boolean {
        val previous = previousMagnitude
        previousMagnitude = magnitude.copyOf()
        if (rms < VOICING_RMS_FLOOR || previous == null) return false
        var rise = 0.0
        var energy = 1e-9
        for (bin in 1 until half) {
            rise += max(0.0, (magnitude[bin] - previous[bin]).toDouble())
            energy += magnitude[bin] + previous[bin]
        }
        return (2.0 * rise / energy).toFloat() > ONSET_FLUX_THRESHOLD
    }

    private fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (sample in samples) sum += sample.toDouble() * sample
        return sqrt(sum / samples.size).toFloat()
    }

    private fun peak(samples: FloatArray): Float {
        var peak = 0f
        for (sample in samples) {
            val value = abs(sample)
            if (value > peak) peak = value
        }
        return peak
    }

    private companion object {
        const val LEVEL_SMOOTHING = 0.35f
        const val BRIGHTNESS_SMOOTHING = 0.5f
        const val PITCH_SMOOTHING = 0.5f
        const val VOICING_RMS_FLOOR = 0.004f
        const val YIN_THRESHOLD = 0.15f
        const val YIN_VOICING_CEILING = 0.30f
        const val ONSET_FLUX_THRESHOLD = 0.12f
    }
}
