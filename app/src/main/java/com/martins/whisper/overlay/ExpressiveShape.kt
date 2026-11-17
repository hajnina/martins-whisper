package com.martins.whisper.overlay

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Material 3 Expressive "squircle": a true superellipse with continuous corners, not a circle and
 * not a rounded rectangle. Geometry lives in pure functions so it can be unit-tested off-device.
 */
object ExpressiveShape {

    /** Superellipse exponent. 2 is a circle/ellipse, 4 is the M3 Expressive continuous corner. */
    const val EXPONENT = 4f

    private const val SEGMENTS = 192

    /**
     * Point on the superellipse |x/a|^n + |y/b|^n = 1 at [angleRadians] (0 = +x, growing clockwise
     * in screen coordinates).
     */
    fun superellipsePoint(
        angleRadians: Double,
        halfWidth: Float,
        halfHeight: Float,
        exponent: Float = EXPONENT,
    ): Pair<Float, Float> {
        val n = exponent.coerceAtLeast(2f)
        val c = cos(angleRadians)
        val s = sin(angleRadians)
        val x = sign(c) * abs(c).pow(2.0 / n) * halfWidth
        val y = sign(s) * abs(s).pow(2.0 / n) * halfHeight
        return x.toFloat() to y.toFloat()
    }

    /** Closed squircle path filling [rect] exactly at its four axis points. */
    fun squircle(rect: RectF): Path {
        val path = Path()
        if (rect.width() <= 0f || rect.height() <= 0f) return path
        val cx = rect.centerX()
        val cy = rect.centerY()
        val halfWidth = rect.width() / 2f
        val halfHeight = rect.height() / 2f
        for (index in 0..SEGMENTS) {
            val angle = 2.0 * PI * index / SEGMENTS
            val (x, y) = superellipsePoint(angle, halfWidth, halfHeight)
            if (index == 0) path.moveTo(cx + x, cy + y) else path.lineTo(cx + x, cy + y)
        }
        path.close()
        return path
    }

    /** Radius of the inscribed circle of a unit squircle at [angleRadians]; used by tests. */
    fun normalizedRadius(angleRadians: Double, exponent: Float = EXPONENT): Float {
        val (x, y) = superellipsePoint(angleRadians, 1f, 1f, exponent)
        return kotlin.math.sqrt(x * x + y * y)
    }
}
