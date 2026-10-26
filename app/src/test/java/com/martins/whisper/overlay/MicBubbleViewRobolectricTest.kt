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
    }

    @Test
    fun `content description tracks every dictation state`() {
        view.state = DictationState.Idle
        assertEquals("Hold to dictate", view.contentDescription)

        view.state = DictationState.Loading()
        assertEquals("Loading model\u2026", view.contentDescription)

        view.state = DictationState.Recording(1_200, 0.5f, true)
        assertEquals("Listening; release to transcribe", view.contentDescription)

        view.state = DictationState.Processing()
        assertEquals("Transcribing\u2026", view.contentDescription)

        view.state = DictationState.Success("Inserted")
        assertEquals("Inserted", view.contentDescription)

        view.state = DictationState.Error("No microphone")
        assertEquals("Error: No microphone", view.contentDescription)
    }

    @Test
    fun `success and error use confirm and reject haptics through the seam`() {
        view.state = DictationState.Success("Inserted")
        assertEquals(listOf(HapticFeedbackConstants.CONFIRM), haptics)

        view.state = DictationState.Error("No microphone")
        assertEquals(
            listOf(HapticFeedbackConstants.CONFIRM, HapticFeedbackConstants.REJECT),
            haptics,
        )
    }

    @Test
    fun `repeating the same state does not repeat haptics`() {
        view.state = DictationState.Success("Inserted")
        view.state = DictationState.Success("Inserted again")
        assertEquals(listOf(HapticFeedbackConstants.CONFIRM), haptics)
    }

    @Test
    fun `press and release emit a virtual key tick through the seam`() {
        view.state = DictationState.Idle
        haptics.clear()

        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val up = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_UP, 10f, 10f, 0)
        try {
            view.onTouchEvent(down)
            view.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }

        assertEquals(
            listOf(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.VIRTUAL_KEY,
            ),
            haptics,
        )
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
