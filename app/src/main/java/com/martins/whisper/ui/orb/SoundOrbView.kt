package com.martins.whisper.ui.orb

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.martins.whisper.core.voice.OrbDynamics
import com.martins.whisper.core.voice.OrbMotion
import com.martins.whisper.core.voice.VoiceFeatures
import com.martins.whisper.overlay.SoundOrbRenderer

/**
 * Reusable living orb for onboarding and any other screen. Feed it real [VoiceFeatures] from the
 * capture path; it never animates a signal that was not measured.
 *
 * - [listening] true: 60 fps ticker while audio is flowing (and a short decay after the last frame).
 * - [idleBreathing] true: slow ticker so a hero orb breathes before the first recording.
 * - both false: no ticker at all (zero cost), one static frame.
 */
class SoundOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val dynamics = OrbDynamics()
    private val renderer = SoundOrbRenderer()
    private val bounds = RectF()

    var accent: Int = DEFAULT_ACCENT
        set(value) {
            field = value
            invalidate()
        }

    var idleBreathing: Boolean = false
        set(value) {
            field = value
            updateTicker()
        }

    var listening: Boolean = false
        set(value) {
            field = value
            updateTicker()
        }

    private var lastFeatures: VoiceFeatures? = null
    private var lastFeaturesAt = 0L
    private var lastFrameAt = 0L
    private val animationsEnabled = AnimationPolicy.enabled()
    private var running = false
    internal var lastMotion: OrbMotion? = null
        private set

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            invalidate()
            postDelayed(this, FRAME_MS)
        }
    }

    /** Push a measured frame; safe to call from any thread. */
    fun onVoiceFeatures(features: VoiceFeatures) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyFeatures(features)
        } else {
            post { applyFeatures(features) }
        }
    }

    private fun applyFeatures(features: VoiceFeatures) {
        lastFeatures = features
        lastFeaturesAt = SystemClock.elapsedRealtime()
        updateTicker()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateTicker()
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    private fun updateTicker() {
        val fresh = lastFeatures != null &&
            SystemClock.elapsedRealtime() - lastFeaturesAt < DECAY_AFTER_MS
        val shouldRun = animationsEnabled && (listening || idleBreathing || fresh)
        if (shouldRun && !running) {
            running = true
            lastFrameAt = 0L
            post(frame)
        } else if (!shouldRun && running) {
            running = false
            removeCallbacks(frame)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        val motion = motionForDraw()
        lastMotion = motion
        renderer.draw(canvas, bounds, motion, accent)
    }

    private fun motionForDraw(): OrbMotion {
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastFrameAt == 0L) 1f / 60f else ((now - lastFrameAt) / 1000f).coerceIn(0f, 0.1f)
        lastFrameAt = now
        val features = lastFeatures
        val fresh = features != null && now - lastFeaturesAt < DECAY_AFTER_MS
        if (fresh && features != null) {
            return dynamics.update(
                level = features.level,
                pitchNorm = features.pitchHz?.let { features.pitchNorm },
                voicedProb = features.voicedProb,
                brightness = features.brightness,
                onset = features.onset,
                dtSeconds = dt,
            )
        }
        return dynamics.idle(dt)
    }

    private companion object {
        const val FRAME_MS = 16L
        const val DECAY_AFTER_MS = 900L
        val DEFAULT_ACCENT = 0xFF135D66.toInt()
    }
}
