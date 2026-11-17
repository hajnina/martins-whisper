package com.martins.whisper.overlay

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import kotlin.math.PI
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class ExpressiveShapeTest {

    @Test
    fun `squircle is not a circle - corners bulge past the inscribed circle`() {
        val diagonal = PI / 4.0
        val squircleRadius = ExpressiveShape.normalizedRadius(diagonal)
        assertTrue("squircle corner radius was $squircleRadius", squircleRadius > 1.08f)
        assertEquals(1f, ExpressiveShape.normalizedRadius(0.0), 0.0001f)
        assertEquals(1f, ExpressiveShape.normalizedRadius(PI / 2), 0.0001f)
        assertNotEquals(1f, squircleRadius)
    }

    @Test
    fun `squircle fills its bounds exactly`() {
        val rect = RectF(0f, 0f, 144f, 144f)
        val path = ExpressiveShape.squircle(rect)
        val bounds = RectF()
        path.computeBounds(bounds, true)
        assertEquals(rect.left, bounds.left, 0.25f)
        assertEquals(rect.top, bounds.top, 0.25f)
        assertEquals(rect.right, bounds.right, 0.25f)
        assertEquals(rect.bottom, bounds.bottom, 0.25f)
    }

    @Test
    fun `squircle perimeter is longer than the inscribed circle`() {
        val rect = RectF(0f, 0f, 72f, 72f)
        val squircleLength = PathMeasure(ExpressiveShape.squircle(rect), false).length
        val circleLength = PathMeasure(
            Path().apply { addCircle(36f, 36f, 36f, Path.Direction.CW) },
            false,
        ).length
        assertTrue("squircle=$squircleLength circle=$circleLength", squircleLength > circleLength * 1.05f)
        assertTrue("squircle=$squircleLength circle=$circleLength", squircleLength < circleLength * 1.25f)
    }

    @Test
    fun `squircle is symmetric and non-degenerate`() {
        val rect = RectF(-20f, -20f, 52f, 52f)
        val path = ExpressiveShape.squircle(rect)
        val bounds = RectF()
        path.computeBounds(bounds, true)
        assertEquals(72f, bounds.width(), 0.25f)
        assertEquals(72f, bounds.height(), 0.25f)
        val length = PathMeasure(path, false).length
        assertTrue("squircle perimeter was $length", length > 2f * PI.toFloat() * 36f)
        val (x, y) = ExpressiveShape.superellipsePoint(PI / 4, 36f, 36f)
        assertEquals(x, y, 0.0001f)
        assertEquals(sqrt(x * x + y * y) / 36f, ExpressiveShape.normalizedRadius(PI / 4), 0.0001f)
    }

    @Test
    fun `degenerate rect yields an empty path instead of crashing`() {
        assertTrue(ExpressiveShape.squircle(RectF(0f, 0f, 0f, 0f)).isEmpty)
        assertTrue(ExpressiveShape.squircle(RectF(5f, 5f, 5f, 40f)).isEmpty)
    }
}
