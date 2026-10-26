package com.martins.whisper.overlay

import android.app.Application
import android.view.MotionEvent
import com.martins.whisper.data.SettingsStore
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowSettings

/**
 * Drives the real bubble view with synthetic MotionEvents and the real OverlayController drag
 * bookkeeping (no window-manager layout involved). The finger's screen coordinates are the
 * rawX/rawY values; the window must follow them while keeping the ACTION_DOWN offset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h1920dp-mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class BubbleDragRobolectricTest {

    private lateinit var app: Application
    private lateinit var controller: OverlayController
    private lateinit var bubble: MicBubbleView
    private var eventTime = 1_000L

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(true)
        controller = OverlayController(app)
        val listener = object : MicBubbleView.Listener {
            override fun onPress() = Unit
            override fun onRelease() = Unit
            override fun onCancel() = Unit
            override fun onDragStart() = controller.beginDrag()
            override fun onDrag(dxFromDown: Float, dyFromDown: Float) =
                controller.dragBy(dxFromDown, dyFromDown)
            override fun onDragEnd() = controller.endDrag()
            override fun onSettingsTap() = Unit
            override fun onStopTap() = Unit
        }
        assertTrue(controller.show(listener))
        bubble = controller.bubbleForTest() ?: error("bubble view missing")
    }

    @After
    fun tearDown() {
        controller.hide()
        ShadowSettings.setCanDrawOverlays(false)
    }

    @Test
    fun `bubble follows the finger from the down point within two pixels`() {
        val start = controller.positionForTest() ?: error("no position")
        val downX = 1000f
        val downY = 950f
        dispatch(MotionEvent.ACTION_DOWN, downX, downY)

        val moves = listOf(
            -60f to -40f,
            -180f to -120f,
            -320f to -200f,
            -420f to -260f,
        )
        for ((dx, dy) in moves) {
            dispatch(MotionEvent.ACTION_MOVE, downX + dx, downY + dy)
            val position = controller.positionForTest()!!
            assertWithinTwoPx(start.first + dx, position.first)
            assertWithinTwoPx(start.second + dy, position.second)
        }

        dispatch(MotionEvent.ACTION_UP, downX - 420f, downY - 260f)
    }

    @Test
    fun `release snaps to the nearest edge and persists it`() {
        val start = controller.positionForTest() ?: error("no position")
        val margin = (12 * app.resources.displayMetrics.density).roundToInt()
        val downX = start.first + 28f
        val downY = start.second + 28f
        dispatch(MotionEvent.ACTION_DOWN, downX, downY)

        dispatch(MotionEvent.ACTION_MOVE, downX - 900f, downY - 100f)
        val beforeRelease = controller.positionForTest()!!
        assertTrue(beforeRelease.first < start.first)

        dispatch(MotionEvent.ACTION_UP, downX - 900f, downY - 100f)

        val snapped = controller.positionForTest()!!
        assertEquals(margin, snapped.first)
        assertEquals(beforeRelease.second, snapped.second)
        val saved = SettingsStore(app)
        assertEquals(snapped.first, saved.bubbleX)
        assertEquals(snapped.second, saved.bubbleY)
    }

    private fun dispatch(action: Int, x: Float, y: Float) {
        eventTime += 16
        val event = MotionEvent.obtain(
            1_000L,
            eventTime,
            action,
            x,
            y,
            0,
        )
        try {
            bubble.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun assertWithinTwoPx(expected: Number, actual: Int) {
        assertTrue(
            "expected $actual within 2 px of $expected",
            abs(expected.toFloat() - actual) <= 2f,
        )
    }
}
