package com.martins.whisper.core.engine

/**
 * Outcome of one engine on one audio buffer. Stable public shape: onboarding (LENA) and
 * language/preference work (ASHA) build on it, and telemetry/sidecar JSON mirror every field.
 *
 * [realTimeFactor] is inferred time divided by audio time (lower is faster), never "total time".
 * A failed engine still carries the timings measured before the failure and an empty [text].
 */
data class EngineResult(
    val engineId: String,
    val text: String,
    val loadMs: Long,
    val inferMs: Long,
    val audioMs: Long,
    val error: String? = null,
) {
    val realTimeFactor: Double
        get() = if (audioMs > 0L) inferMs.toDouble() / audioMs.toDouble() else 0.0

    val ok: Boolean
        get() = error == null
}
