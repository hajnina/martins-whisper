package com.martins.whisper.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.martins.whisper.service.DictationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class MicBubbleViewRobolectricTest {

    private lateinit var view: MicBubbleView
    private val haptics = mutableListOf<Int>()

    @Before
    fun setUp() {
        view = MicBubbleView(RuntimeEnvironment.getApplication())
        haptics.clear()
        view.hapticFeedback = { haptics += it }
        view.scheduleHaptic = { _, action -> action() }
    }

    @Test
    fun `content description tracks every dictation state`() {
        view.state = DictationState.Idle
        assertEquals("Tap to dictate", view.contentDescription)

        view.state = DictationState.Loading()
        assertEquals("Loading model\u2026", view.contentDescription)

        view.state = DictationState.Recording(1_200, 0.5f, true)
        assertEquals("Listening; tap to stop", view.contentDescription)

        view.state = DictationState.Processing()
        assertEquals("Transcribing\u2026", view.contentDescription)

        view.state = DictationState.Success("Inserted")
        assertEquals("Inserted", view.contentDescription)

        view.state = DictationState.Error("No microphone")
        assertEquals("Error: No microphone", view.contentDescription)
    }

    @Test
    fun `success ticks once and error ticks twice through the seam`() {
        view.state = DictationState.Success("Inserted")
        assertEquals(listOf(HapticFeedbackConstants.CONFIRM), haptics)

        view.state = DictationState.Error("No microphone")
        assertEquals(
            listOf(
                HapticFeedbackConstants.CONFIRM,
                HapticFeedbackConstants.REJECT,
                HapticFeedbackConstants.REJECT,
            ),
            haptics,
        )
    }

    @Test
    fun `error double tick schedules the second pulse briefly after the first`() {
        val delays = mutableListOf<Long>()
        view.scheduleHaptic = { delayMs, action ->
            delays += delayMs
            action()
        }

        view.state = DictationState.Error("No microphone")

        assertEquals(listOf(MicBubbleView.ERROR_DOUBLE_TICK_MS), delays)
        assertTrue(delays.single() in 1..150)
        assertEquals(
            listOf(HapticFeedbackConstants.REJECT, HapticFeedbackConstants.REJECT),
            haptics,
        )
    }

    @Test
    fun `state, content description and haptics apply synchronously`() {
        val recording = DictationState.Recording(0L, 0f, false)
        view.state = recording
        assertEquals(recording, view.state)
        assertEquals("Listening; tap to stop", view.contentDescription)

        view.state = DictationState.Processing()
        assertEquals("Transcribing\u2026", view.contentDescription)

        view.state = DictationState.Error("boom")
        assertEquals("Error: boom", view.contentDescription)
    }

    @Test
    fun `repeating the same state does not repeat haptics`() {
        view.state = DictationState.Success("Inserted")
        view.state = DictationState.Success("Inserted again")
        assertEquals(listOf(HapticFeedbackConstants.CONFIRM), haptics)
    }

    @Test
    fun `a tap starts and latches the session and the next tap stops it`() {
        val presses = mutableListOf<String>()
        view.listener = object : MicBubbleView.Listener {
            override fun onPress() {
                presses += "press"
                view.state = DictationState.Recording(0L, 0f, false)
            }

            override fun onRelease() {
                presses += "release"
            }

            override fun onCancel() {
                presses += "cancel"
            }

            override fun onDrag(dxFromDown: Float, dyFromDown: Float) = Unit
            override fun onDragEnd() = Unit
            override fun onSettingsTap() = Unit
            override fun onStopTap() = Unit
        }
        view.state = DictationState.Idle
        haptics.clear()

        tap()
        assertEquals(listOf("press"), presses)
        assertEquals(listOf(HapticFeedbackConstants.VIRTUAL_KEY), haptics)

        view.state = DictationState.Recording(500L, 0.2f, false)
        haptics.clear()
        tap()
        assertEquals(listOf("press", "release"), presses)
        assertEquals(listOf(HapticFeedbackConstants.VIRTUAL_KEY), haptics)
    }

    @Test
    fun `a tap with sub-touch-slop jitter is still a tap, never a drag`() {
        val events = mutableListOf<String>()
        view.listener = object : MicBubbleView.Listener {
            override fun onPress() {
                events += "press"
            }

            override fun onRelease() {
                events += "release"
            }

            override fun onCancel() {
                events += "cancel"
            }

            override fun onDragStart() {
                events += "drag-start"
            }

            override fun onDrag(dxFromDown: Float, dyFromDown: Float) {
                events += "drag"
            }

            override fun onDragEnd() {
                events += "drag-end"
            }

            override fun onSettingsTap() = Unit
            override fun onStopTap() = Unit
        }
        view.state = DictationState.Idle

        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val move = MotionEvent.obtain(0L, 8L, MotionEvent.ACTION_MOVE, 12f, 11f, 0)
        val up = MotionEvent.obtain(0L, 12L, MotionEvent.ACTION_UP, 12f, 11f, 0)
        try {
            view.onTouchEvent(down)
            view.onTouchEvent(move)
            view.onTouchEvent(up)
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }

        assertEquals(listOf("press"), events)
    }

    private fun tap() {
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val up = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_UP, 10f, 10f, 0)
        try {
            view.onTouchEvent(down)
            view.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    @Test
    fun `loading and processing render without crashing`() {
        drawFor(DictationState.Loading())
        assertTrue(view.contentDescription.isNotBlank())

        drawFor(DictationState.Processing())
        assertTrue(view.contentDescription.isNotBlank())
    }

    private fun drawFor(state: DictationState) {
        view.state = state
        view.measure(
            View.MeasureSpec.makeMeasureSpec(view.expandedWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(view.expandedHeightPx, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, view.expandedWidthPx, view.expandedHeightPx)
        val bitmap = Bitmap.createBitmap(view.expandedWidthPx, view.expandedHeightPx, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap.recycle()
    }
}
