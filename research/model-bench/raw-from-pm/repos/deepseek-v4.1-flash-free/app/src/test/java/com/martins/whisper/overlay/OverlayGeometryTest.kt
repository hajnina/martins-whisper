package com.martins.whisper.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayGeometryTest {

    @Test
    fun `clamps into screen with margin`() {
        assertEquals(12 to 12, OverlayGeometry.clamp(-50, -50, 56, 56, 1080, 1920, 12))
        assertEquals(1012 to 1852, OverlayGeometry.clamp(5000, 5000, 56, 56, 1080, 1920, 12))
        assertEquals(500 to 700, OverlayGeometry.clamp(500, 700, 56, 56, 1080, 1920, 12))
    }

    @Test
    fun `default is bottom end above inset`() {
        assertEquals(
            1080 - 56 - 12 to 1920 - 56 - 12 - 72,
            OverlayGeometry.defaultBottomEnd(56, 56, 1080, 1920, 12, 72),
        )
    }

    @Test
    fun `expanded sizes are selected`() {
        assertEquals(240, OverlayGeometry.expandedOrCollapsedWidth(true, 56, 240))
        assertEquals(56, OverlayGeometry.expandedOrCollapsedWidth(false, 56, 240))
    }
}
