package com.martins.whisper.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.martins.whisper.core.voice.OrbDynamics
import com.martins.whisper.core.voice.OrbMotion
import com.martins.whisper.core.voice.VoiceFeatures
import com.martins.whisper.service.DictationState
import com.martins.whisper.ui.orb.AnimationPolicy
import com.martins.whisper.ui.theme.WhisperOverlayPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MicBubbleView(context: Context) : View(context) {

    interface Listener {
        fun onPress()
        fun onRelease()
        fun onCancel()
        fun onDragStart() = Unit
        fun onDrag(dxFromDown: Float, dyFromDown: Float)
        fun onDragEnd()
        fun onSettingsTap()
        fun onStopTap()
    }

    var listener: Listener? = null

    internal var hapticFeedback: (Int) -> Unit = { performHapticFeedback(it) }
    internal var scheduleHaptic: (delayMs: Long, action: () -> Unit) -> Unit =
        { delayMs, action -> postDelayed(action, delayMs) }

    var state: DictationState = DictationState.Idle
        set(value) {
            val previous = field
            field = value
            if (previous::class != value::class) {
                when (value) {
                    is DictationState.Success -> {
                        hapticFeedback(HapticFeedbackConstants.CONFIRM)
                        checkSpring = SpringValue(CHECK_FROM)
                        checkTarget = 1f
                    }
                    is DictationState.Error -> {
                        hapticFeedback(HapticFeedbackConstants.REJECT)
                        scheduleHaptic(ERROR_DOUBLE_TICK_MS) {
                            hapticFeedback(HapticFeedbackConstants.REJECT)
                        }
                        shakeSpring = SpringValue(0f, SHAKE_IMPULSE)
                    }
                    else -> Unit
                }
            }
            expandTarget = if (value is DictationState.Recording) 1f else 0f
            if (!AnimationPolicy.enabled()) {
                expandSpring = SpringValue(expandTarget)
                checkSpring = SpringValue(1f)
                shakeSpring = SpringValue(0f)
            }
            updateMotion(value)
            contentDescription = BubblePresentation.of(value).contentDescription
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isDither = true
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val grillePath: Path by lazy { ExpressiveIcon.glyph(0f, 0f, 1f, 1f) }
    private val scaledGrille = Path()

    private val orbDynamics = OrbDynamics()
    private var lastOrbFrameAt = 0L
    private var previousSpeechDetected = false
    private var latestFeatures: VoiceFeatures? = null
    private var latestFeaturesAt = 0L
    internal var lastOrbMotion: OrbMotion? = null
        private set

    private var expandSpring = SpringValue(0f)
    private var expandTarget = 0f
    private var checkSpring = SpringValue(1f)
    private var checkTarget = 1f
    private var shakeSpring = SpringValue(0f)
    private var levelSpring = SpringValue(0f)
    private var lastFrameAt = 0L
    private var frameRunning = false

    private val frameTick = object : Runnable {
        override fun run() {
            if (!frameRunning) return
            invalidate()
            postDelayed(this, 16)
        }
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var downEventTime = 0L
    private var tracking = false
    private var dragging = false
    private var latchedAtDown = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downEventTime = event.eventTime
                dragging = false
                tracking = true
                // Tap-to-toggle: a short tap starts and latches the session; the next tap stops
                // it. A hold only stops a session this press started, never an existing latch.
                latchedAtDown = state is DictationState.Loading || state is DictationState.Recording
                if (!latchedAtDown) {
                    hapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    listener?.onPress()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return true
                val totalX = event.rawX - downRawX
                val totalY = event.rawY - downRawY
                if (!dragging && (abs(totalX) > touchSlop || abs(totalY) > touchSlop)) {
                    dragging = true
                    listener?.onCancel()
                    listener?.onDragStart()
                }
                if (dragging) {
                    listener?.onDrag(totalX, totalY)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (tracking) {
                    if (dragging) {
                        listener?.onDragEnd()
                    } else {
                        // Exactly one owner stops on release: a hold stops the session this
                        // press started, while a tap stops an already latched session.
                        val held = event.eventTime - downEventTime >= HOLD_THRESHOLD_MS
                        if (held != latchedAtDown) {
                            hapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            listener?.onRelease()
                        }
                    }
                }
                tracking = false
                dragging = false
                latchedAtDown = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (tracking && !dragging) listener?.onCancel()
                tracking = false
                dragging = false
                latchedAtDown = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Measured features from the capture path; safe to call from any thread. */
    fun onVoiceFeatures(features: VoiceFeatures) {
        post {
            latestFeatures = features
            latestFeaturesAt = SystemClock.elapsedRealtime()
            invalidate()
        }
    }

    private fun updateMotion(value: DictationState) {
        if (!AnimationPolicy.enabled()) {
            stopFrameTicker()
            return
        }
        val live = value is DictationState.Recording || value is DictationState.Loading ||
            value is DictationState.Processing || value is DictationState.Success ||
            value is DictationState.Error
        if (live || !SpringSolver.settled(expandSpring, expandTarget)) startFrameTicker()
    }

    private fun startFrameTicker() {
        if (frameRunning) return
        frameRunning = true
        lastFrameAt = 0L
        removeCallbacks(frameTick)
        post(frameTick)
    }

    private fun stopFrameTicker() {
        frameRunning = false
        removeCallbacks(frameTick)
    }

    private fun motionForDraw(now: Long, dt: Float): OrbMotion {
        val current = state
        if (current is DictationState.Recording) {
            val measured = latestFeatures?.takeIf { now - latestFeaturesAt < FEATURE_FRESH_MS }
            if (measured != null) {
                return orbDynamics.update(
                    level = measured.level,
                    pitchNorm = measured.pitchHz?.let { measured.pitchNorm },
                    voicedProb = measured.voicedProb,
                    brightness = measured.brightness,
                    onset = measured.onset,
                    dtSeconds = dt,
                )
            }
            val onset = current.speechDetected && !previousSpeechDetected
            previousSpeechDetected = current.speechDetected
            val voiced = if (current.speechDetected) 1f else 0f
            return orbDynamics.update(current.level, null, voiced, null, onset, dt)
        }
        previousSpeechDetected = false
        return orbDynamics.idle(dt)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastFrameAt == 0L) {
            1f / 60f
        } else {
            ((now - lastFrameAt) / 1000f).coerceIn(0f, 0.05f)
        }
        lastFrameAt = now
        advanceSprings(dt)
        val motion = motionForDraw(now, dt)
        lastOrbMotion = motion

        val heightPx = height.toFloat()
        val widthPx = width.toFloat()
        val expanded = widthPx > heightPx * 1.2f
        val disc = heightPx
        val pillWidth = if (expanded) {
            disc + (widthPx - disc) * expandSpring.position.coerceIn(0f, 1.15f)
        } else {
            disc
        }
        val left = widthPx - pillWidth
        val capsule = RectF(left + dp(1f), dp(1f), widthPx - dp(1f), heightPx - dp(1f))
        val radius = capsule.height() / 2f
        val centerX = capsule.centerX()
        val centerY = capsule.centerY()

        canvas.save()
        val shake = if (AnimationPolicy.enabled()) shakeSpring.position.coerceIn(-dp(6f), dp(6f)) else 0f
        canvas.translate(shake, 0f)

        platePaint.color = WhisperOverlayPalette.InstrumentPlate
        canvas.drawRoundRect(capsule, radius, radius, platePaint)
        bezelPaint.color = WhisperOverlayPalette.InstrumentBezel
        canvas.drawRoundRect(capsule, radius, radius, bezelPaint)

        val recording = state is DictationState.Recording
        if (recording && pillWidth > disc * 1.35f) {
            drawRibbon(canvas, capsule, motion)
            drawStop(canvas, widthPx - dp(35f), centerY)
        } else {
            when (val current = state) {
                is DictationState.Idle -> {
                    drawGrille(canvas, centerX, centerY, GLYPH_TILE)
                    glyphPaint.color = WhisperOverlayPalette.InstrumentStatusDot
                    canvas.drawCircle(
                        centerX + disc * 0.354f,
                        centerY - disc * 0.354f,
                        dp(2.2f),
                        glyphPaint,
                    )
                }
                is DictationState.Recording -> drawCompactRecording(canvas, centerX, centerY, disc)
                is DictationState.Loading, is DictationState.Processing -> {
                    drawProcessingArc(canvas, centerX, centerY, disc)
                    drawGrille(
                        canvas,
                        centerX,
                        centerY,
                        GLYPH_TILE * 0.9f,
                        WhisperOverlayPalette.InstrumentProcessingGlyph,
                    )
                }
                is DictationState.Success -> {
                    drawFilledDisc(canvas, capsule, radius, WhisperOverlayPalette.InstrumentAccent)
                    drawCheck(canvas, centerX, centerY)
                }
                is DictationState.Error -> {
                    drawFilledDisc(canvas, capsule, radius, WhisperOverlayPalette.InstrumentError)
                    drawExclamation(canvas, centerX, centerY)
                }
            }
        }
        canvas.restore()

        if (frameRunning && allSettled()) stopFrameTicker()
    }

    private fun drawFilledDisc(canvas: Canvas, capsule: RectF, radius: Float, color: Int) {
        platePaint.color = color
        canvas.drawRoundRect(capsule, radius, radius, platePaint)
        bezelPaint.color = WhisperOverlayPalette.InstrumentBezel
        canvas.drawRoundRect(capsule, radius, radius, bezelPaint)
    }

    private fun drawGrille(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        tile: Float,
        color: Int = WhisperOverlayPalette.InstrumentGlyph,
    ) {
        val size = minOf(width.toFloat(), height.toFloat())
        grillePath.transform(
            android.graphics.Matrix().apply {
                setScale(size * tile, size * tile)
                postTranslate(centerX, centerY)
            },
            scaledGrille,
        )
        glyphPaint.color = color
        canvas.drawPath(scaledGrille, glyphPaint)
        scaledGrille.reset()
    }

    private fun drawRibbon(canvas: Canvas, capsule: RectF, motion: OrbMotion) {
        val barWidth = dp(4f)
        val gap = dp(4.2f)
        val maxHeight = dp(34f)
        val minHeight = dp(7f)
        val envelope = floatArrayOf(0.30f, 0.52f, 0.72f, 0.88f, 1f, 0.88f, 0.72f, 0.52f, 0.30f)
        val pitch = barWidth + gap
        val total = envelope.size * barWidth + (envelope.size - 1) * gap
        val stopLeft = width - dp(55f)
        val startX = ((stopLeft - dp(14f)) - total) / 2f
        val level = levelSpring.position.coerceIn(0f, 1f)
        val amplitude = (0.32f + 0.68f * level) * (0.55f + 0.45f * motion.radius.coerceIn(0f, 1f))
        glyphPaint.color = WhisperOverlayPalette.InstrumentGlyph
        for (index in envelope.indices) {
            val barHeight = (maxHeight * envelope[index] * amplitude).coerceIn(minHeight, maxHeight)
            val left = startX + index * pitch
            val rect = RectF(
                left,
                capsule.centerY() - barHeight / 2f,
                left + barWidth,
                capsule.centerY() + barHeight / 2f,
            )
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, glyphPaint)
        }
    }

    private fun drawStop(canvas: Canvas, centerX: Float, centerY: Float) {
        glyphPaint.color = WhisperOverlayPalette.InstrumentAccent
        canvas.drawCircle(centerX, centerY, dp(20f), glyphPaint)
        glyphPaint.color = WhisperOverlayPalette.InstrumentOnAccent
        val half = dp(6.5f)
        canvas.drawRoundRect(
            RectF(centerX - half, centerY - half, centerX + half, centerY + half),
            dp(2f),
            dp(2f),
            glyphPaint,
        )
    }

    private fun drawCompactRecording(canvas: Canvas, centerX: Float, centerY: Float, disc: Float) {
        arcPaint.color = WhisperOverlayPalette.InstrumentAccent
        arcPaint.strokeWidth = dp(5.2f)
        val inset = dp(8.6f)
        val rect = RectF(
            centerX - disc / 2f + inset,
            centerY - disc / 2f + inset,
            centerX + disc / 2f - inset,
            centerY + disc / 2f - inset,
        )
        canvas.drawArc(rect, -140f, 280f, false, arcPaint)
        drawStop(canvas, centerX, centerY)
    }

    private fun drawProcessingArc(canvas: Canvas, centerX: Float, centerY: Float, disc: Float) {
        arcPaint.strokeWidth = dp(5.2f)
        val radius = disc / 2f - dp(6.5f)
        val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        for (index in 0 until 8) {
            arcPaint.color = if (index % 2 == 1) {
                WhisperOverlayPalette.InstrumentArcLit
            } else {
                WhisperOverlayPalette.InstrumentArcDim
            }
            canvas.drawArc(rect, -90f + index * 45f + 7f, 31f, false, arcPaint)
        }
    }

    private fun drawCheck(canvas: Canvas, centerX: Float, centerY: Float) {
        val scale = if (AnimationPolicy.enabled()) checkSpring.position.coerceIn(0.7f, 1.1f) else 1f
        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)
        markPaint.color = WhisperOverlayPalette.InstrumentOnAccent
        markPaint.strokeWidth = dp(4.9f)
        val path = Path().apply {
            moveTo(centerX - dp(9f), centerY)
            lineTo(centerX - dp(2.5f), centerY + dp(7f))
            lineTo(centerX + dp(10f), centerY - dp(7f))
        }
        canvas.drawPath(path, markPaint)
        canvas.restore()
    }

    private fun drawExclamation(canvas: Canvas, centerX: Float, centerY: Float) {
        markPaint.color = WhisperOverlayPalette.InstrumentOnError
        markPaint.strokeWidth = dp(5.2f)
        canvas.drawLine(centerX, centerY - dp(13f), centerX, centerY + dp(2f), markPaint)
        glyphPaint.color = WhisperOverlayPalette.InstrumentOnError
        canvas.drawCircle(centerX, centerY + dp(10f), dp(3f), glyphPaint)
    }

    private fun advanceSprings(dt: Float) {
        if (!AnimationPolicy.enabled()) {
            expandSpring = SpringValue(expandTarget)
            checkSpring = SpringValue(1f)
            shakeSpring = SpringValue(0f)
            levelSpring = SpringValue(0f)
            return
        }
        expandSpring = SpringSolver.step(
            expandSpring,
            expandTarget,
            dt,
            stiffness = SpatialFast.stiffness,
            damping = SpatialFast.damping,
        )
        checkSpring = SpringSolver.step(
            checkSpring,
            checkTarget,
            dt,
            stiffness = SpatialFast.stiffness,
            damping = SpatialFast.damping,
        )
        shakeSpring = SpringSolver.step(
            shakeSpring,
            0f,
            dt,
            stiffness = SpatialFast.stiffness,
            damping = SpatialFast.damping,
        )
        val current = state
        val measured = latestFeatures
        val targetLevel = when {
            current is DictationState.Recording && measured != null -> measured.level
            current is DictationState.Recording -> current.level
            else -> 0f
        }.coerceIn(0f, 1f)
        levelSpring = SpringSolver.step(
            levelSpring,
            targetLevel,
            dt,
            stiffness = EffectsFast.stiffness,
            damping = EffectsFast.damping,
        )
    }

    private fun allSettled(): Boolean =
        SpringSolver.settled(expandSpring, expandTarget, epsilon = 0.002f) &&
            SpringSolver.settled(checkSpring, checkTarget, epsilon = 0.002f) &&
            SpringSolver.settled(shakeSpring, 0f, epsilon = 0.01f) &&
            SpringSolver.settled(levelSpring, levelSpring.position, epsilon = 100f) &&
            state !is DictationState.Recording

    internal fun springScaleForTest(): Float = expandSpring.position

    internal fun settleMotionForTest() {
        expandSpring = SpringValue(expandTarget)
        checkSpring = SpringValue(1f)
        shakeSpring = SpringValue(0f)
        levelSpring = SpringValue(1f)
    }

    override fun onDetachedFromWindow() {
        stopFrameTicker()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * density

    internal object SpatialFast {
        const val stiffness = 800f
        const val damping = 33.94f
    }

    internal object SpatialDefault {
        const val stiffness = 380f
        const val damping = 23.39f
    }

    internal object EffectsFast {
        const val stiffness = 3800f
        const val damping = 123.29f
    }

    internal object EffectsDefault {
        const val stiffness = 1600f
        const val damping = 80f
    }

    companion object {
        internal const val ERROR_DOUBLE_TICK_MS = 90L
        internal const val HOLD_THRESHOLD_MS = 275L
        private const val FEATURE_FRESH_MS = 500L
        private const val GLYPH_TILE = 0.46f
        private const val CHECK_FROM = 0.86f
        private const val SHAKE_IMPULSE = -14f
    }
}
