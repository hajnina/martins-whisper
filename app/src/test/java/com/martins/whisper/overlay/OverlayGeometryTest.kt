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
    fun `default is the right edge vertically centred away from the keyboard`() {
        assertEquals(
            1080 - 56 - 12 to (1920 - 56) / 2,
            OverlayGeometry.defaultSide(56, 56, 1080, 1920, 12),
        )
    }

    @Test
    fun `drag moves by finger travel from the down point`() {
        assertEquals(
            1012 - 260 to 932 - 180,
            OverlayGeometry.draggedPosition(
                startX = 1012,
                startY = 932,
                dxFromDown = -260f,
                dyFromDown = -180f,
                width = 56,
                height = 56,
                screenWidth = 1080,
                screenHeight = 1920,
                margin = 12,
            ),
        )
    }

    @Test
    fun `drag clamps to the screen`() {
        assertEquals(
            12 to 1852,
            OverlayGeometry.draggedPosition(
                startX = 1012,
                startY = 932,
                dxFromDown = -5000f,
                dyFromDown = 5000f,
                width = 56,
                height = 56,
                screenWidth = 1080,
                screenHeight = 1920,
                margin = 12,
            ),
        )
    }

    @Test
    fun `release snaps to the nearest horizontal edge`() {
        assertEquals(
            12 to 700,
            OverlayGeometry.snapToEdge(400, 700, 56, 56, 1080, 1920, 12),
        )
        assertEquals(
            1012 to 700,
            OverlayGeometry.snapToEdge(800, 700, 56, 56, 1080, 1920, 12),
        )
        assertEquals(
            12 to 1852,
            OverlayGeometry.snapToEdge(400, 5000, 56, 56, 1080, 1920, 12),
        )
    }

    @Test
    fun `expanded sizes are selected`() {
        assertEquals(240, OverlayGeometry.expandedOrCollapsedWidth(true, 56, 240))
        assertEquals(56, OverlayGeometry.expandedOrCollapsedWidth(false, 56, 240))
    }
}
