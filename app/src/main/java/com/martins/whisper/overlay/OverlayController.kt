package com.martins.whisper.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.martins.whisper.core.voice.VoiceFeatures
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.service.DictationState
import com.martins.whisper.ui.orb.AnimationPolicy

class OverlayController(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val settings = SettingsStore(appContext)

    @Volatile
    private var view: MicBubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragging = false

    val isShowing: Boolean
        get() = view?.isAttachedToWindow == true

    internal val windowFlags: Int
        get() = params?.flags ?: 0

    fun show(listener: MicBubbleView.Listener): Boolean {
        if (isShowing) return true
        if (!Settings.canDrawOverlays(appContext)) return false

        val bubbleView = MicBubbleView(appContext).apply { this.listener = listener }
        val (screenWidth, screenHeight) = screenSize()
        val bubble = dp(72f)
        val margin = dp(12f)
        val default = OverlayGeometry.defaultSide(
            width = bubble,
            height = bubble,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            margin = margin,
        )
        val x: Int
        val y: Int
        if (settings.hasSavedBubblePosition()) {
            val clamped = OverlayGeometry.clamp(
                settings.bubbleX,
                settings.bubbleY,
                bubble,
                bubble,
                screenWidth,
                screenHeight,
                margin,
            )
            x = clamped.first
            y = clamped.second
        } else {
            x = default.first
            y = default.second
        }

        val layoutParams = WindowManager.LayoutParams(
            bubble,
            bubble,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            title = "Martins Whisper bubble"
        }

        return try {
            windowManager.addView(bubbleView, layoutParams)
            view = bubbleView
            params = layoutParams
            true
        } catch (_: Throwable) {
            view = null
            params = null
            false
        }
    }

    fun update(state: DictationState) {
        val bubbleView = view ?: return
        val layoutParams = params ?: return
        resizeFor(state, bubbleView, layoutParams)
        bubbleView.state = state
    }

    /**
     * Recording widens the same window to 180x72, anchored at the edge nearest the screen so the
     * disc under the finger (the stop target) never moves. Reduced motion and narrow screens keep
     * the 72 dp compact ring; loading, processing, success and error are always 72.
     */
    private fun resizeFor(state: DictationState, bubbleView: MicBubbleView, layoutParams: WindowManager.LayoutParams) {
        val (screenWidth, screenHeight) = screenSize()
        val margin = dp(12f)
        val disc = dp(DISC_DP)
        val pill = dp(PILL_DP)
        val expanded = state is DictationState.Recording &&
            AnimationPolicy.enabled() &&
            screenWidth >= pill + margin * 2
        val targetWidth = if (expanded) pill else disc
        if (layoutParams.width == targetWidth && layoutParams.height == disc) return
        val oldWidth = layoutParams.width
        val rightAnchored = layoutParams.x + oldWidth / 2 >= screenWidth / 2
        val proposedX = if (rightAnchored) layoutParams.x + oldWidth - targetWidth else layoutParams.x
        val (x, y) = OverlayGeometry.clamp(
            proposedX,
            layoutParams.y,
            targetWidth,
            disc,
            screenWidth,
            screenHeight,
            margin,
        )
        layoutParams.width = targetWidth
        layoutParams.height = disc
        layoutParams.x = x
        layoutParams.y = y
        runCatching { windowManager.updateViewLayout(bubbleView, layoutParams) }
    }

    /**
     * Measured voice features from the capture thread. Safe to call from any thread: the bubble
     * posts its own state changes onto the main looper.
     */
    fun onVoiceFeatures(features: VoiceFeatures) {
        view?.onVoiceFeatures(features)
    }

    fun beginDrag() {
        val layoutParams = params ?: return
        dragStartX = layoutParams.x
        dragStartY = layoutParams.y
        dragging = true
    }

    /** [dxFromDown]/[dyFromDown] are finger travel since ACTION_DOWN, never view-local deltas. */
    fun dragBy(dxFromDown: Float, dyFromDown: Float) {
        val bubbleView = view ?: return
        val layoutParams = params ?: return
        if (!dragging) beginDrag()
        val (screenWidth, screenHeight) = screenSize()
        val clamped = OverlayGeometry.draggedPosition(
            startX = dragStartX,
            startY = dragStartY,
            dxFromDown = dxFromDown,
            dyFromDown = dyFromDown,
            width = layoutParams.width,
            height = layoutParams.height,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            margin = dp(12f),
        )
        layoutParams.x = clamped.first
        layoutParams.y = clamped.second
        runCatching { windowManager.updateViewLayout(bubbleView, layoutParams) }
    }

    fun endDrag() {
        val bubbleView = view ?: return
        val layoutParams = params ?: return
        if (!dragging) {
            persistPosition()
            return
        }
        dragging = false
        val (screenWidth, screenHeight) = screenSize()
        val snapped = OverlayGeometry.snapToEdge(
            x = layoutParams.x,
            y = layoutParams.y,
            width = layoutParams.width,
            height = layoutParams.height,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            margin = dp(12f),
        )
        layoutParams.x = snapped.first
        layoutParams.y = snapped.second
        runCatching { windowManager.updateViewLayout(bubbleView, layoutParams) }
        persistPosition()
    }

    fun persistPosition() {
        val layoutParams = params ?: return
        settings.saveBubblePosition(layoutParams.x, layoutParams.y)
    }

    internal fun positionForTest(): Pair<Int, Int>? =
        params?.let { it.x to it.y }

    internal fun sizeForTest(): Pair<Int, Int>? =
        params?.let { it.width to it.height }

    internal fun bubbleForTest(): MicBubbleView? = view

    fun hide() {
        val bubbleView = view ?: return
        runCatching { windowManager.removeView(bubbleView) }
        view = null
        params = null
    }

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val display = windowManager.defaultDisplay
            val point = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getRealSize(point)
            point.x to point.y
        }
    }

    private fun dp(value: Float): Int =
        (value * appContext.resources.displayMetrics.density).toInt()

    private companion object {
        const val DISC_DP = 72f
        const val PILL_DP = 180f
    }
}
