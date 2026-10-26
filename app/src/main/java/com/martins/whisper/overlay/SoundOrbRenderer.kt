package com.martins.whisper.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.martins.whisper.core.voice.OrbMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the living orb into the fixed bubble window. All motion comes from [OrbMotion], which is
 * computed from measured audio (or a real state change); this class only paints, it invents nothing.
 *
 * Cost: a handful of circles + one radial gradient per frame, only while a ticker is running.
 */
class SoundOrbRenderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val hsv = FloatArray(3)
    private val lobeColor = IntArray(1)

    fun draw(canvas: Canvas, bounds: RectF, motion: OrbMotion, accent: Int) {
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val base = min(bounds.width(), bounds.height()) / 2f
        if (base <= 0f) return

        val scale = (0.62f + 0.30f * motion.radius + 0.03f * motion.breathe).coerceIn(0.35f, 1f)
        val core = base * scale

        Color.colorToHSV(accent, hsv)
        val hue = (hsv[0] + (motion.hue - 0.5f) * 36f + 360f) % 360f
        val light = Color.HSVToColor(floatArrayOf(hue, hsv[1] * 0.9f, (hsv[2] * 1.45f).coerceAtMost(1f)))
        val deep = Color.HSVToColor(floatArrayOf(hue, hsv[1], (hsv[2] * 0.72f).coerceAtLeast(0.05f)))
        val edge = Color.HSVToColor(floatArrayOf(hue, hsv[1], (hsv[2] * 0.5f).coerceAtLeast(0.04f)))

        fill.shader = null
        fill.style = Paint.Style.FILL
        for (index in 0 until motion.lobes) {
            val angle = 2.0 * PI * index / motion.lobes + motion.breathe * 0.35
            val distance = core * (0.20f + 0.10f * motion.wobble)
            val lobeRadius = core * (0.46f + 0.10f * motion.radius - 0.04f * motion.wobble)
            fill.color = mix(light, deep, 0.35f + 0.4f * motion.wobble)
            fill.alpha = (70f + 80f * motion.radius).toInt().coerceIn(0, 255)
            canvas.drawCircle(
                cx + (cos(angle) * distance).toFloat(),
                cy + (sin(angle) * distance).toFloat() + (motion.hue - 0.5f) * core * 0.28f,
                lobeRadius,
                fill,
            )
        }

        fill.alpha = 255
        fill.shader = RadialGradient(
            cx - core * 0.22f,
            cy - core * 0.28f,
            core * 1.25f,
            intArrayOf(light, deep, edge),
            floatArrayOf(0f, (0.45f + 0.4f * motion.edgeCrispness).coerceAtMost(0.9f), 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, core, fill)
        fill.shader = null

        stroke.color = light
        stroke.alpha = (30f + 190f * motion.edgeCrispness).toInt().coerceIn(0, 255)
        stroke.strokeWidth = (base * (0.015f + 0.045f * motion.edgeCrispness)).coerceAtLeast(1f)
        canvas.drawCircle(cx, cy, core, stroke)

        if (motion.wobble > 0.08f) {
            fill.color = edge
            fill.alpha = (90f * motion.wobble).toInt().coerceIn(0, 255)
            val dots = 10
            for (index in 0 until dots) {
                val angle = 2.0 * PI * index / dots + motion.breathe * 0.6
                val distance = core * (0.35f + 0.5f * ((index * 37 % 100) / 100f))
                val dot = core * (0.03f + 0.03f * motion.wobble)
                canvas.drawCircle(
                    cx + (cos(angle) * distance).toFloat(),
                    cy + (sin(angle) * distance).toFloat(),
                    dot,
                    fill,
                )
            }
        }

        if (motion.ripple > 0.02f) {
            stroke.color = light
            stroke.alpha = (motion.ripple * 170f).toInt().coerceIn(0, 255)
            stroke.strokeWidth = (base * 0.06f).coerceAtLeast(1f)
            val ringRadius = core * (1f + 0.5f * (1f - motion.ripple))
            canvas.drawCircle(cx, cy, ringRadius, stroke)
        }
    }

    private fun mix(from: Int, to: Int, factor: Float): Int {
        val t = factor.coerceIn(0f, 1f)
        lobeColor[0] = Color.rgb(
            (Color.red(from) * (1f - t) + Color.red(to) * t).toInt(),
            (Color.green(from) * (1f - t) + Color.green(to) * t).toInt(),
            (Color.blue(from) * (1f - t) + Color.blue(to) * t).toInt(),
        )
        return lobeColor[0]
    }
}
