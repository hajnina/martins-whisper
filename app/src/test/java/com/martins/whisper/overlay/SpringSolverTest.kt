package com.martins.whisper.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpringSolverTest {

    @Test
    fun `underdamped spring overshoots then settles`() {
        var state = SpringValue(0f)
        var peak = 0f
        repeat(180) {
            state = SpringSolver.step(state, 1f, 1f / 60f)
            peak = maxOf(peak, state.position)
        }
        assertTrue("expected overshoot above target, peak=$peak", peak > 1.02f)
        assertTrue("expected no runaway, peak=$peak", peak < 1.6f)
        assertEquals(1f, state.position, 0.01f)
        assertTrue(abs(state.velocity) < 0.2f)
        assertTrue(SpringSolver.settled(state, 1f, epsilon = 0.01f))
    }

    @Test
    fun `critically damped spring never overshoots`() {
        var state = SpringValue(0f)
        var peak = 0f
        repeat(120) {
            state = SpringSolver.step(
                state,
                1f,
                1f / 60f,
                stiffness = SpringSolver.STIFFNESS,
                damping = SpringSolver.criticalDamping(),
            )
            peak = maxOf(peak, state.position)
        }
        assertTrue("critically damped peak=$peak", peak <= 1.0001f)
        assertEquals(1f, state.position, 0.01f)
    }

    @Test
    fun `large or malformed frames never produce NaN or extreme values`() {
        val cases = listOf(0f, -1f, 0.5f, 100f, Float.NaN, Float.POSITIVE_INFINITY)
        for (dt in cases) {
            val state = SpringSolver.step(SpringValue(0f, 0f), 1.12f, dt)
            assertTrue("dt=$dt position=${state.position}", state.position.isFinite())
            assertTrue("dt=$dt velocity=${state.velocity}", state.velocity.isFinite())
            assertTrue("dt=$dt position=${state.position}", abs(state.position) < 10f)
            assertTrue("dt=$dt velocity=${state.velocity}", abs(state.velocity) < 1_000f)
        }
    }

    @Test
    fun `non-finite input state snaps back to the target`() {
        val state = SpringSolver.step(
            SpringValue(Float.NaN, Float.POSITIVE_INFINITY),
            target = 1f,
            dtSeconds = 1f / 60f,
        )
        assertEquals(1f, state.position, 0f)
        assertEquals(0f, state.velocity, 0f)
    }

    @Test
    fun `error shake decays back to rest`() {
        var state = SpringValue(0f, -22f)
        var travel = 0f
        repeat(120) {
            state = SpringSolver.step(state, 0f, 1f / 60f, stiffness = 420f, damping = 14f)
            travel = maxOf(travel, abs(state.position))
        }
        assertTrue("shake should be visible, travel=$travel", travel in 0.5f..6f)
        assertEquals(0f, state.position, 0.01f)
        assertTrue(SpringSolver.settled(state, 0f, epsilon = 0.01f))
    }

    @Test
    fun `settled rejects a moving spring`() {
        assertFalse(SpringSolver.settled(SpringValue(1f, 5f), 1f))
    }
}
