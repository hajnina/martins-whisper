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
import kotlin.math.min

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
        val circleCenter = min(width, height) / 2f
        val circleRadius = min(width, height) / 2f - dp(1f)
        val circleRect = RectF(
            circleCenter - circleRadius,
            circleCenter - circleRadius,
            circleCenter + circleRadius,
            circleCenter + circleRadius,
        )
        surfacePaint.color = WhisperOverlayPalette.BubbleSurface
        canvas.drawCircle(circleCenter, circleCenter, circleRadius, surfacePaint)
        ringPaint.color = WhisperOverlayPalette.BubbleRing
        ringPaint.strokeWidth = dp(1f)
        canvas.drawCircle(circleCenter, circleCenter, circleRadius - dp(0.5f), ringPaint)
        val orbMotion = motionForDraw()
        lastOrbMotion = orbMotion
        orbRenderer.draw(canvas, circleRect, orbMotion, visual.accent)

        when (visual.icon) {
            BubbleIcon.SPINNER -> drawSpinner(canvas, circleRect)
            BubbleIcon.TRANSCRIBING -> drawTranscribing(canvas, circleCenter)
            BubbleIcon.CHECK -> drawCheck(canvas, circleCenter)
            BubbleIcon.CROSS -> drawCross(canvas, circleCenter)
            BubbleIcon.MIC, BubbleIcon.MIC_LEVELS -> drawMic(canvas, circleCenter, circleRadius)
        }
    }

    private fun drawMic(canvas: Canvas, center: Float, radius: Float) {
        iconPaint.color = WhisperOverlayPalette.OnBubble
        iconPaint.strokeWidth = dp(2.2f)
        iconPaint.strokeCap = Paint.Cap.ROUND
        val bodyWidth = radius * 0.44f
        val bodyHeight = radius * 0.58f
        val bodyTop = center - radius * 0.42f
        val bodyRect = RectF(
            center - bodyWidth / 2f,
            bodyTop,
            center + bodyWidth / 2f,
            bodyTop + bodyHeight,
        )
        canvas.drawRoundRect(bodyRect, bodyWidth / 2f, bodyWidth / 2f, iconPaint)
        val arcRect = RectF(
            center - radius * 0.45f,
            center - radius * 0.18f,
            center + radius * 0.45f,
            center + radius * 0.5f,
        )
        canvas.drawArc(arcRect, 0f, 180f, false, iconPaint)
        canvas.drawLine(center, center + radius * 0.45f, center, center + radius * 0.66f, iconPaint)
        canvas.drawLine(
            center - radius * 0.24f,
            center + radius * 0.66f,
            center + radius * 0.24f,
            center + radius * 0.66f,
            iconPaint,
        )
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

    override fun onDetachedFromWindow() {
        stopSpinner()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * density

    companion object {
        internal const val ERROR_DOUBLE_TICK_MS = 90L
        private const val FEATURE_FRESH_MS = 500L
    }
}
