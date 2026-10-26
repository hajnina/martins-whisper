package com.martins.whisper.core.voice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Motion state for the living orb. Every field is derived from a *measured* signal:
 * level from RMS, hue from YIN pitch, lobes from spectral centroid, wobble from voicing,
 * ripple from a real onset. Missing signals keep the previous visual value; nothing is faked.
 */
data class OrbMotion(
    /** Spring-damped radius, 0..1 (0 = dot, 1 = full bubble). */
    val radius: Float,
    /** Short expansion ring after an onset, decays to 0. */
    val ripple: Float,
    /** Hue position 0..1 (low/deep voice = 0, high/light voice = 1); 0.5 until measured. */
    val hue: Float,
    /** Number of lobes, 3..7; driven by spectral brightness. */
    val lobes: Int,
    /** Edge crispness 0..1 (soft haze .. sharp rim); driven by spectral brightness. */
    val edgeCrispness: Float,
    /** Grainy surface 0..1 when the frame is unvoiced or noisy. */
    val wobble: Float,
    /** Slow idle breathing, -1..1. */
    val breathe: Float,
)

/**
 * Spring/decay dynamics for [OrbMotion]: never jittery, never overshoots audibly, and
 * independent of the frame rate (dt is clamped). Pure Kotlin, host-testable.
 */
class OrbDynamics(
    private val stiffness: Float = 90f,
    private val damping: Float = 18f,
    private val rippleDecayPerSecond: Float = 4f,
    private val breathPeriodSeconds: Float = 4f,
) {
    private var radius = 0f
    private var velocity = 0f
    private var ripple = 0f
    private var hue = 0.5f
    private var lobes = 4
    private var edge = 0.5f
    private var wobble = 0f
    private var phase = 0f

    fun update(
        level: Float,
        pitchNorm: Float?,
        voicedProb: Float,
        brightness: Float?,
        onset: Boolean,
        dtSeconds: Float,
    ): OrbMotion {
        val dt = dtSeconds.coerceIn(0f, 0.1f)
        val target = level.coerceIn(0f, 1f)
        velocity += (target - radius) * stiffness * dt
        velocity *= exp(-damping * dt)
        radius = (radius + velocity * dt).coerceIn(0f, 1.1f)

        if (pitchNorm != null) {
            hue += (pitchNorm.coerceIn(0f, 1f) - hue) * (1f - exp(-10f * dt))
        }
        if (brightness != null) {
            val measured = brightness.coerceIn(0f, 1f)
            lobes = (3f + measured * 4f).roundToInt().coerceIn(3, 7)
            edge += (measured - edge) * (1f - exp(-8f * dt))
        }
        val targetWobble = if (voicedProb < 0.5f) target else 0f
        wobble += (targetWobble - wobble) * (1f - exp(-8f * dt))

        if (onset) ripple = 1f
        ripple *= exp(-rippleDecayPerSecond * dt)

        phase += dt
        return OrbMotion(
            radius = radius,
            ripple = ripple.coerceIn(0f, 1f),
            hue = hue.coerceIn(0f, 1f),
            lobes = lobes,
            edgeCrispness = edge.coerceIn(0f, 1f),
            wobble = wobble.coerceIn(0f, 1f),
            breathe = sin(2.0 * PI * phase / breathPeriodSeconds).toFloat(),
        )
    }

    /** No measured audio: only the slow breath, so an idle orb still feels alive. */
    fun idle(dtSeconds: Float): OrbMotion {
        val dt = dtSeconds.coerceIn(0f, 0.1f)
        radius += (0f - radius) * (1f - exp(-6f * dt))
        velocity *= exp(-12f * dt)
        ripple *= exp(-rippleDecayPerSecond * dt)
        wobble += (0f - wobble) * (1f - exp(-8f * dt))
        phase += dt
        return OrbMotion(
            radius = radius.coerceIn(0f, 1.1f),
            ripple = ripple.coerceIn(0f, 1f),
            hue = hue,
            lobes = lobes,
            edgeCrispness = edge.coerceIn(0f, 1f),
            wobble = wobble.coerceIn(0f, 1f),
            breathe = sin(2.0 * PI * phase / breathPeriodSeconds).toFloat(),
        )
    }

    /** Settled radius for tests/diagnostics. */
    val currentRadius: Float get() = radius

    fun keepHue(): Float = hue

    fun isSettled(target: Float): Boolean = abs(radius - target) < 0.02f
}
