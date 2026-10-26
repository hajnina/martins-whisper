package com.martins.whisper.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.martins.whisper.service.DictationState
import kotlin.math.abs
import kotlin.math.min

class MicBubbleView(context: Context) : View(context) {

    interface Listener {
        fun onPress()
        fun onRelease()
        fun onCancel()
        fun onDrag(dx: Float, dy: Float)
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
            contentDescription = BubblePresentation.of(value).contentDescription
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val bubbleSizePx = dp(56f)
    val expandedWidthPx = dp(240f).toInt()
    val expandedHeightPx = dp(64f).toInt()

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val maxTextWidth = dp(240f) - dp(124f)

    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val timerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(15f)
        isFakeBoldText = true
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(12f)
        alpha = 220
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
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var downAt = 0L
    private var tracking = false
    private var dragging = false
    private var pendingControl: Control? = null

    private enum class Control { SETTINGS, STOP }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                downAt = SystemClock.elapsedRealtime()
                dragging = false
                val control = controlAt(event.x, event.y)
                if (control != null) {
                    pendingControl = control
                    tracking = false
                } else {
                    pendingControl = null
                    tracking = true
                    hapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    listener?.onPress()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pendingControl != null) return true
                if (!tracking) return true
                val totalX = event.rawX - downRawX
                val totalY = event.rawY - downRawY
                if (!dragging && (abs(totalX) > touchSlop || abs(totalY) > touchSlop)) {
                    dragging = true
                    listener?.onCancel()
                }
                if (dragging) {
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    listener?.onDrag(dx, dy)
                }
                lastRawX = event.rawX
                lastRawY = event.rawY
                return true
            }

            MotionEvent.ACTION_UP -> {
                pendingControl?.let {
                    if (it == Control.SETTINGS) listener?.onSettingsTap() else listener?.onStopTap()
                    pendingControl = null
                    return true
                }
                if (tracking) {
                    if (dragging) {
                        listener?.onDragEnd()
                    } else {
                        hapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        listener?.onRelease()
                    }
                }
                tracking = false
                dragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pendingControl = null
                if (tracking && !dragging) listener?.onCancel()
                tracking = false
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun controlAt(x: Float, y: Float): Control? {
        if (state is DictationState.Idle) return null
        val height = height.toFloat().takeIf { it > 0f } ?: expandedHeightPx.toFloat()
        val width = width.toFloat().takeIf { it > 0f } ?: expandedWidthPx.toFloat()
        if (y < height * 0.15f || y > height * 0.85f) return null
        val stopCenter = width - dp(52f)
        val gearCenter = width - dp(20f)
        return when {
            abs(x - gearCenter) <= dp(17f) -> Control.SETTINGS
            abs(x - stopCenter) <= dp(17f) -> Control.STOP
            else -> null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val height = height.toFloat()
        val width = width.toFloat()
        val expanded = state !is DictationState.Idle
        val visual = BubblePresentation.of(state)
        val accent = visual.accent

        if (expanded) {
            surfacePaint.color = Color.argb(242, 22, 28, 32)
            canvas.drawRoundRect(RectF(0f, 0f, width, height), height / 2f, height / 2f, surfacePaint)
            ringPaint.color = Color.argb(70, 255, 255, 255)
            ringPaint.strokeWidth = dp(1f)
            canvas.drawRoundRect(
                RectF(dp(0.5f), dp(0.5f), width - dp(0.5f), height - dp(0.5f)),
                height / 2f,
                height / 2f,
                ringPaint,
            )
        }

        val circleCenter = if (expanded) height / 2f else min(width, height) / 2f
        val circleRadius = if (expanded) height / 2f - dp(3f) else min(width, height) / 2f - dp(1f)
        val circleRect = RectF(
            circleCenter - circleRadius,
            circleCenter - circleRadius,
            circleCenter + circleRadius,
            circleCenter + circleRadius,
        )
        surfacePaint.color = accent
        canvas.drawOval(circleRect, surfacePaint)

        when (visual.icon) {
            BubbleIcon.SPINNER -> drawSpinner(canvas, circleRect)
            BubbleIcon.TRANSCRIBING -> drawTranscribing(canvas, circleCenter)
            BubbleIcon.CHECK -> drawCheck(canvas, circleCenter)
            BubbleIcon.CROSS -> drawCross(canvas, circleCenter)
            BubbleIcon.MIC, BubbleIcon.MIC_LEVELS -> drawMic(canvas, circleCenter, circleRadius)
        }

        if (expanded) {
            drawExpandedContent(canvas, width, height)
            drawGear(canvas, width - dp(20f), height / 2f)
            drawStop(canvas, width - dp(52f), height / 2f)
        }
    }

    private fun drawExpandedContent(canvas: Canvas, width: Float, height: Float) {
        val textX = dp(64f)
        val centerY = height / 2f
        when (val current = state) {
            is DictationState.Recording -> {
                timerPaint.textAlign = Paint.Align.LEFT
                val text = formatElapsed(current.elapsedMs)
                canvas.drawText(text, textX, centerY + dp(5f), timerPaint)
                drawLevelBars(canvas, textX + dp(58f), centerY, current.level)
            }
            is DictationState.Loading -> {
                labelPaint.textAlign = Paint.Align.LEFT
                drawEllipsized(canvas, current.label, textX, centerY + dp(4f), maxTextWidth)
            }
            is DictationState.Processing -> {
                labelPaint.textAlign = Paint.Align.LEFT
                drawEllipsized(canvas, current.label, textX, centerY + dp(4f), maxTextWidth)
            }
            is DictationState.Success -> {
                labelPaint.textAlign = Paint.Align.LEFT
                drawEllipsized(canvas, current.message, textX, centerY + dp(4f), maxTextWidth)
            }
            is DictationState.Error -> {
                labelPaint.textAlign = Paint.Align.LEFT
                drawEllipsized(canvas, current.message, textX, centerY + dp(4f), maxTextWidth)
            }
            DictationState.Idle -> Unit
        }
    }

    private fun drawEllipsized(canvas: Canvas, text: String, x: Float, baseline: Float, maxWidth: Float) {
        val available = (width - dp(78f) - x).coerceAtLeast(dp(40f))
        val clipped = TextUtils.ellipsize(text, labelPaint, min(available, maxWidth), TextUtils.TruncateAt.END)
        canvas.drawText(clipped, 0, clipped.length, x, baseline, labelPaint)
    }

    private fun drawLevelBars(canvas: Canvas, startX: Float, centerY: Float, level: Float) {
        val barWidth = dp(4f)
        val gap = dp(3f)
        val maxHeight = dp(24f)
        val normalized = level.coerceIn(0f, 1f)
        fillPaint.color = Color.WHITE
        for (index in 0 until 5) {
            val factor = when (index) {
                0 -> 0.35f
                1 -> 0.6f
                2 -> 1f
                3 -> 0.7f
                4 -> 0.4f
                else -> 0.5f
            }
            val barHeight = (maxHeight * (0.15f + normalized * factor)).coerceAtLeast(dp(3f))
            val left = startX + index * (barWidth + gap)
            val rect = RectF(left, centerY - barHeight / 2f, left + barWidth, centerY + barHeight / 2f)
            fillPaint.alpha = 200
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, fillPaint)
        }
        fillPaint.alpha = 255
    }

    private fun drawMic(canvas: Canvas, center: Float, radius: Float) {
        iconPaint.color = Color.WHITE
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
        iconPaint.color = Color.WHITE
        iconPaint.strokeWidth = dp(2.4f)
        iconPaint.strokeCap = Paint.Cap.ROUND
        val inset = rect.width() * 0.18f
        val arcRect = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        canvas.drawArc(arcRect, spinnerAngle, 260f, false, iconPaint)
    }

    private fun drawTranscribing(canvas: Canvas, center: Float) {
        fillPaint.color = Color.WHITE
        val radius = dp(2.6f)
        val gap = dp(7f)
        for (index in -1..1) {
            canvas.drawCircle(center + index * gap, center, radius, fillPaint)
        }
    }

    private fun drawCheck(canvas: Canvas, center: Float) {
        iconPaint.color = Color.WHITE
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
        iconPaint.color = Color.WHITE
        iconPaint.strokeWidth = dp(2.6f)
        iconPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(center - dp(7f), center - dp(7f), center + dp(7f), center + dp(7f), iconPaint)
        canvas.drawLine(center + dp(7f), center - dp(7f), center - dp(7f), center + dp(7f), iconPaint)
    }

    private fun drawGear(canvas: Canvas, cx: Float, cy: Float) {
        iconPaint.color = Color.argb(225, 255, 255, 255)
        iconPaint.strokeWidth = dp(1.6f)
        iconPaint.strokeCap = Paint.Cap.ROUND
        val inner = dp(3.4f)
        val outer = dp(8f)
        canvas.drawCircle(cx, cy, inner, iconPaint)
        for (index in 0 until 8) {
            val angle = Math.toRadians((index * 45).toDouble())
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            canvas.drawLine(
                cx + cos * (inner + dp(1.4f)),
                cy + sin * (inner + dp(1.4f)),
                cx + cos * outer,
                cy + sin * outer,
                iconPaint,
            )
        }
    }

    private fun drawStop(canvas: Canvas, cx: Float, cy: Float) {
        fillPaint.color = Color.argb(225, 255, 255, 255)
        val half = dp(5.5f)
        canvas.drawRoundRect(
            RectF(cx - half, cy - half, cx + half, cy + half),
            dp(2f),
            dp(2f),
            fillPaint,
        )
    }

    private fun startSpinner() {
        if (spinnerRunning) return
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

    fun formatElapsed(elapsedMs: Long): String = BubbleFormat.elapsed(elapsedMs)

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    companion object {
        internal const val ERROR_DOUBLE_TICK_MS = 90L
    }
}
