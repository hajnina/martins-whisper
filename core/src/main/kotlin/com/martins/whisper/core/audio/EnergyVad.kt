package com.martins.whisper.core.audio

import com.martins.whisper.core.Vad
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

class EnergyVad(
    private val minSpeechRmsDb: Double = -45.0,
    private val thresholdMultiplier: Double = 3.0,
    private val hangoverFrames: Int = 8,
    private val noiseFloorAlpha: Double = 0.02,
) : Vad {
    private var noiseFloorDb: Double = -90.0
    private var hangoverRemaining = 0

    override fun isSpeech(frame: ShortArray): Boolean {
        val db = amplitudeDb(AudioMath.rms(frame))
        val multiplierDb = if (thresholdMultiplier > 1.0) {
            20.0 * log10(thresholdMultiplier)
        } else {
            0.0
        }
        val thresholdDb = max(
            noiseFloorDb + 6.0,
            max(minSpeechRmsDb, noiseFloorDb + multiplierDb),
        )
        val speech = db > thresholdDb

        if (speech) {
            hangoverRemaining = hangoverFrames.coerceAtLeast(0)
        } else {
            noiseFloorDb += noiseFloorAlpha * (db - noiseFloorDb)
            if (hangoverRemaining > 0) {
                hangoverRemaining--
                return true
            }
        }
        return speech
    }

    override fun reset() {
        noiseFloorDb = -90.0
        hangoverRemaining = 0
    }

    private fun amplitudeDb(rms: Double): Double =
        if (rms <= 0.0) -120.0 else 20.0 * log10(rms)

    internal fun noiseFloorForTest(): Double = noiseFloorDb

    @Suppress("unused")
    private fun linearToDb(value: Double): Double = 20.0 * log10(value.coerceAtLeast(1e-12))

    @Suppress("unused")
    private fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)
}
