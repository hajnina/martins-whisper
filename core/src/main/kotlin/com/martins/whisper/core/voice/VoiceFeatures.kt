package com.martins.whisper.core.voice

/**
 * One analysed frame of captured voice, in the shape the living visual needs.
 *
 * All values are already smoothed by [VoiceFeatureExtractor]; consumers (SoundOrb) must not
 * smooth them again. Everything here is measured from real PCM, never synthesised.
 */
data class VoiceFeatures(
    /** Smoothed RMS mapped from dBFS to 0..1 (silence = 0, near full scale = 1). */
    val level: Float,
    /** Smoothed peak sample mapped the same way as [level]; >= [level]. */
    val peakLevel: Float,
    /** Fundamental frequency in Hz, or null when the frame is unvoiced/silent. */
    val pitchHz: Float?,
    /** Log-scaled, per-speaker-normalised pitch in 0..1 (0 = low voice, 1 = high voice). */
    val pitchNorm: Float,
    /** Confidence that the frame is voiced, 0..1. */
    val voicedProb: Float,
    /** Spectral centroid normalised to 0..1 (0 = dark/warm, 1 = bright/noisy). */
    val brightness: Float,
    /** True on a spectral-flux rise (syllable/word start). */
    val onset: Boolean,
    /** Capture timestamp of the analysed frame, milliseconds. */
    val timestampMs: Long,
) {
    companion object {
        val SILENT = VoiceFeatures(
            level = 0f,
            peakLevel = 0f,
            pitchHz = null,
            pitchNorm = 0f,
            voicedProb = 0f,
            brightness = 0f,
            onset = false,
            timestampMs = 0L,
        )
    }
}
