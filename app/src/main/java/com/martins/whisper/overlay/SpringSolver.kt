package com.martins.whisper.overlay

import kotlin.math.abs
import kotlin.math.sqrt

/** One scalar of a damped spring. */
data class SpringValue(val position: Float, val velocity: Float = 0f)

/**
 * Minimal damped-spring integrator (semi-implicit Euler, fixed micro-steps). No new dependency:
 * androidx.dynamicanimation is not on the classpath, and the overlay already runs a frame ticker.
 * Pure and total: every input returns a finite value.
 */
object SpringSolver {

    const val STIFFNESS = 320f
    const val DAMPING = 16f
    private const val MAX_STEP_SECONDS = 1f / 240f
    private const val MAX_FRAME_SECONDS = 0.1f

    fun step(
        current: SpringValue,
        target: Float,
        dtSeconds: Float,
        stiffness: Float = STIFFNESS,
        damping: Float = DAMPING,
    ): SpringValue {
        if (!dtSeconds.isFinite() || dtSeconds <= 0f) return current.sanitized(target)
        var position = current.position
        var velocity = current.velocity
        if (!position.isFinite() || !velocity.isFinite()) return SpringValue(target)
        var remaining = dtSeconds.coerceAtMost(MAX_FRAME_SECONDS)
        while (remaining > 0f) {
            val step = if (remaining < MAX_STEP_SECONDS) remaining else MAX_STEP_SECONDS
            val acceleration = stiffness * (target - position) - damping * velocity
            velocity += acceleration * step
            position += velocity * step
            remaining -= step
        }
        if (!position.isFinite() || !velocity.isFinite()) return SpringValue(target)
        return SpringValue(position, velocity)
    }

    fun settled(state: SpringValue, target: Float, epsilon: Float = 0.001f): Boolean =
        abs(state.position - target) <= epsilon && abs(state.velocity) <= epsilon * 40f

    /** Critical damping for [stiffness]; useful when a caller wants no overshoot. */
    fun criticalDamping(stiffness: Float = STIFFNESS): Float = 2f * sqrt(stiffness)

    private fun SpringValue.sanitized(target: Float): SpringValue =
        if (position.isFinite() && velocity.isFinite()) this else SpringValue(target)
}
