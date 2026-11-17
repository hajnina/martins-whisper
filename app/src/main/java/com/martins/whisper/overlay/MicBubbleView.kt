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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

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
            if (value is DictationState.Loading || value is DictationState.Processing) {
                startSpinner()
            } else {
                stopSpinner()
            }
            if (previous::class != value::class) {
                when (value) {
                    is DictationState.Success -> hapticFeedback(HapticFeedbackConstants.CONFIRM)
                    is DictationState.Error -> {
                        hapticFeedback(HapticFeedbackConstants.REJECT)
                        scheduleHaptic(ERROR_DOUBLE_TICK_MS) {
                            hapticFeedback(HapticFeedbackConstants.REJECT)
                        }
                    }
                    else -> Unit
                }
            }
            updateOrbAnimation(value)
            startExpressiveMotion(previous, value)
            contentDescription = BubblePresentation.of(value).contentDescription
            invalidate()
        }

    private val density = resources.displayMetrics.density

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val orbDynamics = OrbDynamics()
    private val orbRenderer = SoundOrbRenderer()
    private var orbRunning = false
    private var lastOrbFrameAt = 0L
    private var previousSpeechDetected = false
    private var stopOrbAfter: Runnable? = null
    private var latestFeatures: VoiceFeatures? = null
    private var latestFeaturesAt = 0L
    internal var lastOrbMotion: OrbMotion? = null
        private set
    private val orbTick = object : Runnable {
        override fun run() {
            if (!orbRunning) return
            invalidate()
            postDelayed(this, 16)
        }
    }

    private var spinnerAngle = 0f
    private var spinnerRunning = false
    private val spinnerTick = object : Runnable {
        override fun run() {
            if (!spinnerRunning) return
            spinnerAngle = (spinnerAngle + 12f) % 360f
            invalidate()
            postDelayed(this, 16)
        }
    }

    private var scaleSpring = SpringValue(1f)
    private var scaleTarget = 1f
    private var shakeSpring = SpringValue(0f)
    private var shakeTarget = 0f
    private var popSettleAt = 0L
    private var popPending = false
    private var lastSpringFrameAt = 0L
    private var expressiveRunning = false
    private var breathRunning = false
    private var cachedSquircle: Path? = null
    private var cachedGlyph: Path? = null
    private var cachedSize = -1

    private val expressiveTick = object : Runnable {
        override fun run() {
            if (!expressiveRunning) return
            invalidate()
            postDelayed(this, 16)
        }
    }

    private val breathTick = object : Runnable {
        override fun run() {
            if (!breathRunning) return
            invalidate()
            postDelayed(this, 100)
        }
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var tracking = false
    private var dragging = false
    private var stopOnUp = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                tracking = true
                // Tap-to-toggle: the first tap starts and latches the session; the next tap
                // stops it. A tap must never be turned into a stop by its own ACTION_UP.
                stopOnUp = state is DictationState.Loading || state is DictationState.Recording
                if (!stopOnUp) {
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
                    } else if (stopOnUp) {
                        hapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        listener?.onRelease()
                    }
                }
                tracking = false
                dragging = false
                stopOnUp = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (tracking && !dragging) listener?.onCancel()
                tracking = false
                dragging = false
                stopOnUp = false
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
            if (state is DictationState.Recording) startOrb()
            invalidate()
        }
    }

    /**
     * The orb only animates on a real state: while recording (level is measured RMS) and for a
     * short decay after success/error. Idle and loading stay static except for their spinner.
     */
    private fun updateOrbAnimation(value: DictationState) {
        if (!AnimationPolicy.enabled()) {
            stopOrb()
            return
        }
        stopOrbAfter?.let { removeCallbacks(it) }
        stopOrbAfter = null
        when (value) {
            is DictationState.Idle -> stopOrb()
            is DictationState.Recording -> startOrb()
            is DictationState.Success, is DictationState.Error -> {
                startOrb()
                val stop = Runnable { stopOrb() }
                stopOrbAfter = stop
                postDelayed(stop, 1_200L)
            }
            else -> Unit
        }
    }

    private fun startOrb() {
        if (!AnimationPolicy.enabled()) return
        orbRunning = true
        lastOrbFrameAt = SystemClock.elapsedRealtime()
        removeCallbacks(orbTick)
        post(orbTick)
    }

    private fun stopOrb() {
        orbRunning = false
        removeCallbacks(orbTick)
    }

    private fun motionForDraw(): OrbMotion {
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastOrbFrameAt == 0L) 1f / 60f else ((now - lastOrbFrameAt) / 1000f).coerceIn(0f, 0.1f)
        lastOrbFrameAt = now
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
        val visual = BubblePresentation.of(state)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - dp(1f)
        ensureShapeCache(centerX, centerY, radius)
        val shape = cachedSquircle

        val scale = expressiveScale()
        val shake = if (AnimationPolicy.enabled()) shakeSpring.position else 0f
        canvas.save()
        canvas.translate(centerX + shake, centerY)
        canvas.scale(scale, scale)
        canvas.translate(-centerX, -centerY)

        surfacePaint.color = WhisperOverlayPalette.BubbleSurface
        if (shape != null) canvas.drawPath(shape, surfacePaint)
        val orbMotion = motionForDraw()
        lastOrbMotion = orbMotion
        val orbRect = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius,
        )
        if (shape != null) {
            canvas.save()
            canvas.clipPath(shape)
        }
        orbRenderer.draw(canvas, orbRect, orbMotion, visual.accent)
        if (shape != null) canvas.restore()

        ringPaint.color = WhisperOverlayPalette.BubbleRing
        ringPaint.strokeWidth = dp(1f)
        if (shape != null) canvas.drawPath(shape, ringPaint)

        when (visual.icon) {
            BubbleIcon.SPINNER -> drawSpinner(canvas, orbRect)
            BubbleIcon.TRANSCRIBING -> drawTranscribing(canvas, centerY)
            BubbleIcon.CHECK -> drawCheck(canvas, centerX)
            BubbleIcon.CROSS -> drawCross(canvas, centerX)
            BubbleIcon.MIC, BubbleIcon.MIC_LEVELS -> drawGlyph(canvas, centerX, centerY)
        }
        canvas.restore()
    }

    private fun drawGlyph(canvas: Canvas, centerX: Float, centerY: Float) {
        val glyph = cachedGlyph ?: return
        fillPaint.style = Paint.Style.FILL
        fillPaint.shader = null
        fillPaint.alpha = 255
        fillPaint.color = WhisperOverlayPalette.BubbleGlyph
        canvas.drawPath(glyph, fillPaint)
    }

    private fun ensureShapeCache(centerX: Float, centerY: Float, radius: Float) {
        val key = (radius * 100f).toInt()
        if (key == cachedSize && cachedSquircle != null && cachedGlyph != null) return
        cachedSquircle = ExpressiveShape.squircle(
            RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
        )
        cachedGlyph = ExpressiveIcon.glyph(centerX, centerY, min(width, height).toFloat())
        cachedSize = key
    }

    private fun drawSpinner(canvas: Canvas, rect: RectF) {
        iconPaint.color = WhisperOverlayPalette.OnBubble
        iconPaint.strokeWidth = dp(2.4f)
        iconPaint.strokeCap = Paint.Cap.ROUND
        val inset = rect.width() * 0.18f
        val arcRect = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        canvas.drawArc(arcRect, spinnerAngle, 260f, false, iconPaint)
    }

    private fun drawTranscribing(canvas: Canvas, center: Float) {
        fillPaint.color = WhisperOverlayPalette.OnBubble
        val radius = dp(2.6f)
        val gap = dp(7f)
        for (index in -1..1) {
            canvas.drawCircle(center + index * gap, center, radius, fillPaint)
        }
    }

    private fun drawCheck(canvas: Canvas, center: Float) {
        iconPaint.color = WhisperOverlayPalette.OnBubble
        iconPaint.strokeWidth = dp(2.6f)
        iconPaint.strokeCap = Paint.Cap.ROUND
        iconPaint.strokeJoin = Paint.Join.ROUND
        val path = Path().apply {
            moveTo(center - dp(9f), center)
            lineTo(center - dp(2.5f), center + dp(7f))
            lineTo(center + dp(10f), center - dp(7f))
        }
        canvas.drawPath(path, iconPaint)
    }

    private fun drawCross(canvas: Canvas, center: Float) {
        iconPaint.color = WhisperOverlayPalette.OnBubble
        iconPaint.strokeWidth = dp(2.6f)
        iconPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(center - dp(7f), center - dp(7f), center + dp(7f), center + dp(7f), iconPaint)
        canvas.drawLine(center + dp(7f), center - dp(7f), center - dp(7f), center + dp(7f), iconPaint)
    }

    private fun startExpressiveMotion(previous: DictationState, value: DictationState) {
        if (!AnimationPolicy.enabled()) {
            stopExpressiveTicker()
            stopBreathTicker()
            scaleSpring = SpringValue(1f)
            scaleTarget = 1f
            shakeSpring = SpringValue(0f)
            shakeTarget = 0f
            popPending = false
            return
        }
        val changed = previous::class != value::class
        if (changed) {
            when (value) {
                is DictationState.Success -> {
                    scaleSpring = SpringValue(POP_FROM)
                    scaleTarget = POP_PEAK
                    popPending = true
                    popSettleAt = SystemClock.elapsedRealtime() + POP_HOLD_MS
                    startExpressiveTicker()
                }
                is DictationState.Error -> {
                    shakeSpring = SpringValue(0f, SHAKE_IMPULSE)
                    shakeTarget = 0f
                    startExpressiveTicker()
                }
                else -> {
                    scaleTarget = 1f
                    shakeTarget = 0f
                    if (SpringSolver.settled(scaleSpring, scaleTarget) &&
                        value !is DictationState.Loading
                    ) {
                        stopExpressiveTicker()
                    } else {
                        startExpressiveTicker()
                    }
                }
            }
        }
        if (value is DictationState.Idle && isAttachedToWindow) startBreathTicker() else stopBreathTicker()
    }

    private fun expressiveScale(): Float {
        if (!AnimationPolicy.enabled()) return 1f
        val now = SystemClock.elapsedRealtime()
        if (popPending && now >= popSettleAt) {
            popPending = false
            scaleTarget = 1f
        }
        val dt = if (lastSpringFrameAt == 0L) {
            1f / 60f
        } else {
            ((now - lastSpringFrameAt) / 1000f).coerceIn(0f, 0.05f)
        }
        lastSpringFrameAt = now
        scaleSpring = SpringSolver.step(
            scaleSpring,
            scaleTarget,
            dt,
            stiffness = POP_STIFFNESS,
            damping = POP_DAMPING,
        )
        shakeSpring = SpringSolver.step(
            shakeSpring,
            shakeTarget,
            dt,
            stiffness = SHAKE_STIFFNESS,
            damping = SHAKE_DAMPING,
        )
        if (!popPending &&
            SpringSolver.settled(scaleSpring, scaleTarget) &&
            SpringSolver.settled(shakeSpring, shakeTarget, epsilon = 0.01f)
        ) {
            stopExpressiveTicker()
        }
        val current = state
        val level = (current as? DictationState.Recording)?.level?.coerceIn(0f, 1f) ?: 0f
        val live = if (current is DictationState.Recording) 1f + RECORDING_GAIN * level else 1f
        val breath = if (current is DictationState.Idle) {
            1f + BREATH_AMPLITUDE * sin(2.0 * PI * now / BREATH_PERIOD_MS).toFloat()
        } else {
            1f
        }
        return (scaleSpring.position * live * breath).coerceIn(0.8f, 1.35f)
    }

    internal fun springScaleForTest(): Float = expressiveScale()

    private fun startExpressiveTicker() {
        if (expressiveRunning || !AnimationPolicy.enabled()) return
        expressiveRunning = true
        lastSpringFrameAt = 0L
        removeCallbacks(expressiveTick)
        post(expressiveTick)
    }

    private fun stopExpressiveTicker() {
        expressiveRunning = false
        removeCallbacks(expressiveTick)
    }

    private fun startBreathTicker() {
        if (breathRunning || !AnimationPolicy.enabled()) return
        breathRunning = true
        post(breathTick)
    }

    private fun stopBreathTicker() {
        breathRunning = false
        removeCallbacks(breathTick)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (state is DictationState.Idle) startBreathTicker()
    }

    override fun onDetachedFromWindow() {
        stopSpinner()
        stopExpressiveTicker()
        stopBreathTicker()
        super.onDetachedFromWindow()
    }

    private fun startSpinner() {
        if (spinnerRunning) return
        if (!AnimationPolicy.enabled()) {
            invalidate()
            return
        }
        spinnerRunning = true
        post(spinnerTick)
    }

    private fun stopSpinner() {
        spinnerRunning = false
        removeCallbacks(spinnerTick)
    }

    private fun dp(value: Float): Float = value * density

    companion object {
        internal const val ERROR_DOUBLE_TICK_MS = 90L
        private const val FEATURE_FRESH_MS = 500L

        private const val POP_FROM = 0.92f
        private const val POP_PEAK = 1.12f
        private const val POP_HOLD_MS = 150L
        private const val POP_STIFFNESS = 300f
        private const val POP_DAMPING = 13f
        private const val SHAKE_IMPULSE = -22f
        private const val SHAKE_STIFFNESS = 420f
        private const val SHAKE_DAMPING = 14f
        private const val RECORDING_GAIN = 0.06f
        private const val BREATH_AMPLITUDE = 0.012f
        private const val BREATH_PERIOD_MS = 2_600.0
    }
}
