package com.martins.whisper.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbDynamicsTest {

    private fun settle(dynamics: OrbDynamics, level: Float, frames: Int = 120): OrbMotion {
        var motion = dynamics.update(level, null, level, null, false, 1f / 60f)
        repeat(frames) { motion = dynamics.update(level, null, level, null, false, 1f / 60f) }
        return motion
    }

    @Test
    fun `radius follows the measured level and stays bounded`() {
        val quiet = settle(OrbDynamics(), 0.15f)
        val loud = settle(OrbDynamics(), 0.85f)

        assertTrue("quiet=${quiet.radius} loud=${loud.radius}", loud.radius > quiet.radius)
        assertTrue(loud.radius in 0f..1.1f)
        assertEquals(0.85f, loud.radius, 0.05f)
    }

    @Test
    fun `silence decays the orb back down`() {
        val dynamics = OrbDynamics()
        settle(dynamics, 0.9f)
        var motion = dynamics.idle(1f / 60f)
        repeat(180) { motion = dynamics.idle(1f / 60f) }
        assertTrue("radius=${motion.radius}", motion.radius < 0.05f)
    }

    @Test
    fun `onset fires a ripple that decays`() {
        val dynamics = OrbDynamics()
        val hit = dynamics.update(0.5f, null, 1f, null, true, 1f / 60f)
        assertEquals(1f, hit.ripple, 0.1f)

        var later = hit
        repeat(90) { later = dynamics.update(0.5f, null, 1f, null, false, 1f / 60f) }
        assertTrue("ripple=${later.ripple}", later.ripple < 0.05f)
    }

    @Test
    fun `pitch drives hue only when it is actually measured`() {
        val dynamics = OrbDynamics()
        var motion = dynamics.update(0.4f, 0.1f, 1f, null, false, 1f / 60f)
        repeat(60) { motion = dynamics.update(0.4f, 0.1f, 1f, null, false, 1f / 60f) }
        assertTrue("low voice hue=${motion.hue}", motion.hue < 0.25f)

        repeat(60) { motion = dynamics.update(0.4f, 0.9f, 1f, null, false, 1f / 60f) }
        assertTrue("high voice hue=${motion.hue}", motion.hue > 0.75f)

        val held = motion.hue
        repeat(30) { motion = dynamics.update(0.4f, null, 0f, null, false, 1f / 60f) }
        assertEquals("unvoiced must not invent pitch", held, motion.hue, 1e-4f)
        assertTrue("unvoiced adds grain", motion.wobble > 0.2f)
    }

    @Test
    fun `brightness decides the lobe count within bounds`() {
        val dark = OrbDynamics()
        val bright = OrbDynamics()
        var darkMotion = dark.update(0.5f, null, 1f, 0f, false, 1f / 60f)
        var brightMotion = bright.update(0.5f, null, 1f, 1f, false, 1f / 60f)
        repeat(10) {
            darkMotion = dark.update(0.5f, null, 1f, 0f, false, 1f / 60f)
            brightMotion = bright.update(0.5f, null, 1f, 1f, false, 1f / 60f)
        }
        assertEquals(3, darkMotion.lobes)
        assertEquals(7, brightMotion.lobes)
    }

    @Test
    fun `idle breathing stays small and never runs away`() {
        val dynamics = OrbDynamics()
        val breathe = (0 until 600).map { dynamics.idle(1f / 60f).breathe }
        assertTrue(breathe.all { it in -1f..1f })
        assertTrue("breath must move", breathe.max() - breathe.min() > 1f)
    }

    @Test
    fun `a huge dt cannot destabilise the spring`() {
        val dynamics = OrbDynamics()
        val motion = dynamics.update(1f, null, 1f, 1f, true, 10f)
        assertTrue(motion.radius.isFinite())
        assertTrue(motion.radius in 0f..1.1f)
        assertTrue(motion.ripple in 0f..1f)
    }
}
