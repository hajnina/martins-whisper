package com.martins.whisper.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.martins.whisper.core.voice.VoiceFeatures
import com.martins.whisper.service.DictationState
import com.martins.whisper.ui.orb.AnimationPolicy
import com.martins.whisper.ui.theme.WhisperOverlayPalette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Precision Instrument bubble: one full-radius capsule that is 72x72 at rest and 180x72 while
 * recording, with a 2 dp paper bezel. All geometry and colour roles live here; the window only
 * decides whether the expanded width is available.
 */
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
            if (value is DictationState.Loading) startSpinner() else stopSpinner()
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
            previousTone = plateColorFor(previous)
            toneSpring = SpringValue(0f)
            toneTarget = 1f
            if (value is DictationState.Success) {
                checkScale = SpringValue(CHECK_FROM)
                checkTarget = 1f
            } else if (value !is DictationState.Success) {
                checkTarget = 1f
            }
            if (value is DictationState.Error && AnimationPolicy.enabled()) {
                shakeSpring = SpringValue(-dp(SHAKE_DP), 0f)
            } else if (AnimationPolicy.enabled()) {
                shakeSpring = SpringValue(0f)
            }
            startMotion()
            contentDescription = BubblePresentation.of(value).contentDescription
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glyphPath = Path()

    private var latestFeatures: VoiceFeatures? = null
    private var latestFeaturesAt = 0L
    private var ribbonLevel = 0.55f

    private var widthSpring = SpringValue(0f)
    private var toneSpring = SpringValue(1f)
    private var toneTarget = 1f
    private var previousTone = WhisperOverlayPalette.InstrumentPlate
    private var checkScale = SpringValue(1f)
    private var checkTarget = 1f
    private var shakeSpring = SpringValue(0f)
    private var motionRunning = false
    private var lastFrameAt = 0L

    private val motionTick = object : Runnable {
        override fun run() {
            if (!motionRunning) return
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
                if (!dragging && (kotlin.math.abs(totalX) > touchSlop || kotlin.math.abs(totalY) > touchSlop)) {
                    dragging = true
                    listener?.onCancel()
                    listener?.onDragStart()
                }
                if (dragging) listener?.onDrag(totalX, totalY)
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
            if (state is DictationState.Recording) {
                startMotion()
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.elapsedRealtime()
        val dt = frameDelta(now)
        stepSprings(dt)

        val recording = state is DictationState.Recording
        val expanded = recording && width > height * 1.4f
        val target = if (expanded) 1f else 0f
        if (!AnimationPolicy.enabled() || !expanded) widthSpring = SpringValue(target)
        val morph = widthSpring.position.coerceIn(0f, 1f)
        val collapsedWidth = dp(DISC_DP)
        val drawnWidth = collapsedWidth + (width - collapsedWidth) * morph
        val left = width - drawnWidth
        val top = 0f
        val right = width.toFloat()
        val bottom = height.toFloat()
        val radius = min(drawnWidth, bottom) / 2f
        val plate = RectF(left, top, right, bottom)
        val inner = RectF(
            left + dp(BEZEL_DP),
            top + dp(BEZEL_DP),
            right - dp(BEZEL_DP),
            bottom - dp(BEZEL_DP),
        )

        val tone = if (AnimationPolicy.enabled()) {
            ColorUtils.lerp(previousTone, plateColorFor(state), toneSpring.position.coerceIn(0f, 1f))
        } else {
            plateColorFor(state)
        }
        val shake = if (AnimationPolicy.enabled()) shakeSpring.position else 0f

        canvas.save()
        canvas.translate(shake, 0f)
        drawCapsule(canvas, plate, radius, WhisperOverlayPalette.InstrumentPaper)
        drawCapsule(canvas, inner, (radius - dp(BEZEL_DP)).coerceAtLeast(0f), tone)

        val cx = plate.centerX()
        val cy = plate.centerY()
        when (state) {
            is DictationState.Idle -> {
                drawMic(canvas, cx, cy, WhisperOverlayPalette.InstrumentGlyph)
                drawStatusDot(canvas, cx, cy)
            }
            is DictationState.Loading -> {
                drawMic(canvas, cx, cy, WhisperOverlayPalette.InstrumentMuted)
                drawSegmentArc(canvas, cx, cy, radius, litOffset = spinnerAngle)
            }
            is DictationState.Recording -> {
                if (morph > 0.55f) {
                    drawRibbon(canvas, cx)
                    drawStopDisc(canvas, plate.right)
                } else {
                    drawCompactRecording(canvas, cx, cy, radius)
                }
            }
            is DictationState.Processing -> {
                drawMic(canvas, cx, cy, WhisperOverlayPalette.InstrumentMuted)
                drawSegmentArc(canvas, cx, cy, radius, litOffset = 0f)
            }
            is DictationState.Success -> drawCheck(canvas, cx, cy, checkScale.position)
            is DictationState.Error -> drawErrorMark(canvas, cx, cy)
        }
        canvas.restore()
        if (motionSettled()) stopMotion()
    }

    private fun drawCapsule(canvas: Canvas, rect: RectF, radius: Float, color: Int) {
        fillPaint.color = color
        fillPaint.alpha = 255
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
    }

    private fun drawMic(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        val bodyWidth = dp(6.6f)
        val bodyHeight = dp(12.2f)
        val bodyCenter = cy - dp(7.2f)
        fillPaint.color = color
        fillPaint.alpha = 255
        canvas.drawRoundRect(
            RectF(cx - bodyWidth / 2f, bodyCenter - bodyHeight / 2f, cx + bodyWidth / 2f, bodyCenter + bodyHeight / 2f),
            bodyWidth / 2f,
            bodyWidth / 2f,
            fillPaint,
        )
        strokePaint.color = color
        strokePaint.strokeWidth = dp(2.6f)
        val cradle = dp(9.4f)
        canvas.drawArc(
            RectF(cx - cradle, cy - dp(3.2f) - cradle, cx + cradle, cy - dp(3.2f) + cradle),
            0f,
            180f,
            false,
            strokePaint,
        )
        canvas.drawLine(cx, cy + dp(6.2f), cx, cy + dp(11f), strokePaint)
    }

    private fun drawStatusDot(canvas: Canvas, cx: Float, cy: Float) {
        val angle = -PI_OVER_4
        val distance = dp(24.5f)
        fillPaint.color = WhisperOverlayPalette.InstrumentPaper
        fillPaint.alpha = 255
        canvas.drawCircle(
            cx + distance * cos(angle).toFloat(),
            cy + distance * sin(angle).toFloat(),
            dp(STATUS_DOT_DP) / 2f,
            fillPaint,
        )
    }

    private fun drawRibbon(canvas: Canvas, cx: Float) {
        fillPaint.color = WhisperOverlayPalette.InstrumentPaper
        fillPaint.alpha = 255
        val amp = ribbonLevel.coerceIn(0f, 1f)
        val envelope = floatArrayOf(0.34f, 0.54f, 0.74f, 0.9f, 1f, 0.9f, 0.74f, 0.54f, 0.34f)
        val pitch = dp(6.3f)
        val barWidth = dp(2.6f)
        val ribbonCenter = (width - dp(STOP_ZONE_DP)) / 2f + dp(4f)
        val base = height / 2f
        for (index in envelope.indices) {
            val x = ribbonCenter + (index - envelope.size / 2) * pitch
            val barHeight = dp(4f) + dp(20f) * envelope[index] * (0.35f + 0.65f * amp)
            canvas.drawRoundRect(
                RectF(x - barWidth / 2f, base - barHeight / 2f, x + barWidth / 2f, base + barHeight / 2f),
                barWidth / 2f,
                barWidth / 2f,
                fillPaint,
            )
        }
    }

    private fun drawStopDisc(canvas: Canvas, right: Float) {
        val cx = right - dp(31.5f)
        val cy = height / 2f
        fillPaint.color = WhisperOverlayPalette.InstrumentAccent
        fillPaint.alpha = 255
        canvas.drawCircle(cx, cy, dp(18f), fillPaint)
        val half = dp(6.75f)
        fillPaint.color = WhisperOverlayPalette.InstrumentOnAccent
        canvas.drawRoundRect(
            RectF(cx - half, cy - half, cx + half, cy + half),
            dp(2f),
            dp(2f),
            fillPaint,
        )
    }

    private fun drawCompactRecording(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        strokePaint.color = WhisperOverlayPalette.InstrumentAccent
        strokePaint.strokeWidth = dp(5.2f)
        val ring = radius - dp(7.5f)
        canvas.drawArc(
            RectF(cx - ring, cy - ring, cx + ring, cy + ring),
            -90f,
            282f,
            false,
            strokePaint,
        )
        fillPaint.color = WhisperOverlayPalette.InstrumentPaper
        fillPaint.alpha = 255
        val half = dp(7f)
        canvas.drawRoundRect(
            RectF(cx - half, cy - half, cx + half, cy + half),
            dp(2f),
            dp(2f),
            fillPaint,
        )
    }

    private fun drawSegmentArc(canvas: Canvas, cx: Float, cy: Float, radius: Float, litOffset: Float) {
        strokePaint.strokeWidth = dp(5.2f)
        strokePaint.strokeCap = Paint.Cap.BUTT
        val ring = radius - dp(7.5f)
        val arcRect = RectF(cx - ring, cy - ring, cx + ring, cy + ring)
        for (index in 0 until SEGMENTS) {
            val start = index * (360f / SEGMENTS) + 7.5f + litOffset
            val lit = index == 0 || index == 1 || index == 6 || index == 7
            strokePaint.color = if (lit) {
                WhisperOverlayPalette.InstrumentPaper
            } else {
                WhisperOverlayPalette.InstrumentSegmentOff
            }
            canvas.drawArc(arcRect, start, SEGMENT_SWEEP, false, strokePaint)
        }
        strokePaint.strokeCap = Paint.Cap.ROUND
    }

    private fun drawCheck(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        canvas.save()
        canvas.scale(scale.coerceIn(0.5f, 1.2f), scale.coerceIn(0.5f, 1.2f), cx, cy)
        glyphPath.reset()
        glyphPath.moveTo(cx - dp(13.7f), cy - dp(3.5f))
        glyphPath.lineTo(cx - dp(6.7f), cy + dp(4.7f))
        glyphPath.lineTo(cx + dp(13.7f), cy - dp(12f))
        strokePaint.color = WhisperOverlayPalette.InstrumentOnAccent
        strokePaint.strokeWidth = dp(4.9f)
        canvas.drawPath(glyphPath, strokePaint)
        canvas.restore()
    }

    private fun drawErrorMark(canvas: Canvas, cx: Float, cy: Float) {
        strokePaint.color = WhisperOverlayPalette.InstrumentOnError
        strokePaint.strokeWidth = dp(5.4f)
        canvas.drawLine(cx, cy - dp(12f), cx, cy + dp(4f), strokePaint)
        fillPaint.color = WhisperOverlayPalette.InstrumentOnError
        fillPaint.alpha = 255
        canvas.drawCircle(cx, cy + dp(13f), dp(3f), fillPaint)
    }

    private fun plateColorFor(value: DictationState): Int = when (value) {
        is DictationState.Success -> WhisperOverlayPalette.InstrumentAccent
        is DictationState.Error -> WhisperOverlayPalette.InstrumentError
        else -> WhisperOverlayPalette.InstrumentPlate
    }

    private fun frameDelta(now: Long): Float {
        val dt = if (lastFrameAt == 0L) 1f / 60f else ((now - lastFrameAt) / 1000f).coerceIn(0f, 0.05f)
        lastFrameAt = now
        return dt
    }

    private fun stepSprings(dt: Float) {
        if (!AnimationPolicy.enabled()) return
        widthSpring = SpringSolver.step(
            widthSpring,
            if (state is DictationState.Recording && width > height * 1.4f) 1f else 0f,
            dt,
            stiffness = SPATIAL_FAST_K,
            damping = spatialDamping(SPATIAL_FAST_K),
        )
        toneSpring = SpringSolver.step(
            toneSpring,
            toneTarget,
            dt,
            stiffness = EFFECTS_FAST_K,
            damping = effectsDamping(EFFECTS_FAST_K),
        )
        checkScale = SpringSolver.step(
            checkScale,
            checkTarget,
            dt,
            stiffness = SPATIAL_FAST_K,
            damping = spatialDamping(SPATIAL_FAST_K),
        )
        shakeSpring = SpringSolver.step(
            shakeSpring,
            0f,
            dt,
            stiffness = SPATIAL_FAST_K,
            damping = spatialDamping(SPATIAL_FAST_K),
        )
        val current = state
        if (current is DictationState.Recording) {
            val measured = latestFeatures?.takeIf { SystemClock.elapsedRealtime() - latestFeaturesAt < FEATURE_FRESH_MS }
            val targetLevel = measured?.level ?: current.level
            ribbonLevel = SpringSolver.step(
                SpringValue(ribbonLevel),
                targetLevel.coerceIn(0f, 1f),
                dt,
                stiffness = EFFECTS_FAST_K,
                damping = effectsDamping(EFFECTS_FAST_K),
            ).position
        }
    }

    private fun motionSettled(): Boolean =
        SpringSolver.settled(widthSpring, if (state is DictationState.Recording && width > height * 1.4f) 1f else 0f, 0.002f) &&
            SpringSolver.settled(toneSpring, toneTarget, 0.002f) &&
            SpringSolver.settled(checkScale, checkTarget, 0.002f) &&
            SpringSolver.settled(shakeSpring, 0f, 0.01f) &&
            state !is DictationState.Recording

    private fun startMotion() {
        if (!AnimationPolicy.enabled() || motionRunning) return
        motionRunning = true
        lastFrameAt = 0L
        removeCallbacks(motionTick)
        post(motionTick)
    }

    private fun stopMotion() {
        motionRunning = false
        removeCallbacks(motionTick)
    }

    private fun startSpinner() {
        if (spinnerRunning || !AnimationPolicy.enabled()) return
        spinnerRunning = true
        post(spinnerTick)
    }

    private fun stopSpinner() {
        spinnerRunning = false
        removeCallbacks(spinnerTick)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startMotion()
    }

    override fun onDetachedFromWindow() {
        stopSpinner()
        stopMotion()
        super.onDetachedFromWindow()
    }

    internal fun springScaleForTest(): Float = checkScale.position

    internal fun ribbonLevelForTest(): Float = ribbonLevel

    /** Renderer seam: jumps every spring to its resting value for a deterministic still. */
    internal fun settleMotionForTest() {
        val recording = state is DictationState.Recording
        widthSpring = SpringValue(if (recording && width > height * 1.4f) 1f else 0f)
        toneSpring = SpringValue(toneTarget)
        checkScale = SpringValue(checkTarget)
        shakeSpring = SpringValue(0f)
        previousTone = plateColorFor(state)
        ribbonLevel = (state as? DictationState.Recording)?.level?.coerceIn(0f, 1f) ?: ribbonLevel
        stopMotion()
        invalidate()
    }

    private fun dp(value: Float): Float = value * density

    private fun spatialDamping(stiffness: Float): Float = 2f * SPATIAL_ZETA * sqrt(stiffness)

    private fun effectsDamping(stiffness: Float): Float = 2f * EFFECTS_ZETA * sqrt(stiffness)

    private object ColorUtils {
        fun lerp(from: Int, to: Int, fraction: Float): Int {
            val t = fraction.coerceIn(0f, 1f)
            val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t).toInt()
            val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt()
            val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt()
            val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
            return Color.argb(a, r, g, b)
        }
    }

    companion object {
        internal const val ERROR_DOUBLE_TICK_MS = 90L
        internal const val HOLD_THRESHOLD_MS = 275L
        private const val FEATURE_FRESH_MS = 500L

        private const val DISC_DP = 72f
        private const val BEZEL_DP = 2f
        private const val STATUS_DOT_DP = 4.4f
        private const val STOP_ZONE_DP = 64f
        private const val SEGMENTS = 8
        private const val SEGMENT_SWEEP = 30f
        private const val CHECK_FROM = 0.86f
        private const val SHAKE_DP = 2.5f

        private const val SPATIAL_ZETA = 0.6f
        private const val EFFECTS_ZETA = 1f
        private const val SPATIAL_FAST_K = 800f
        private const val EFFECTS_FAST_K = 3800f
        private const val PI_OVER_4 = -Math.PI / 4.0
    }
}
