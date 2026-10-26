package com.martins.whisper.harness

import android.content.Context
import android.view.WindowManager
import com.martins.whisper.overlay.MicBubbleView
import com.martins.whisper.overlay.OverlayController
import com.martins.whisper.service.DictationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * Drives the overlay as the service does, one DictationState at a time, and asserts what the
 * bubble actually exposes. Robolectric does not attach overlay windows, so only registered views
 * and their state/contentDescription are asserted; isShowing is compared with the real attachment
 * flag instead of being assumed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class OverlayStateMachineRobolectricTest {

    private lateinit var context: Context

    private val listener = object : MicBubbleView.Listener {
        override fun onPress() = Unit
        override fun onRelease() = Unit
        override fun onCancel() = Unit
        override fun onDrag(dx: Float, dy: Float) = Unit
        override fun onDragEnd() = Unit
        override fun onSettingsTap() = Unit
        override fun onStopTap() = Unit
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(true)
    }

    @Test
    fun `bubble walks idle loading recording processing success error idle`() {
        val controller = OverlayController(context)
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        assertTrue(controller.show(listener))
        controller.update(DictationState.Idle)
        val bubble = bubbleViews(windowManager).single()
        assertEquals(bubble.isAttachedToWindow, controller.isShowing)
        assertBubble(bubble, DictationState.Idle, "Tap to dictate")

        val loading = DictationState.Loading("Loading Base\u2026")
        controller.update(loading)
        assertBubble(bubble, loading, "Loading Base\u2026")

        val recording = DictationState.Recording(elapsedMs = 1_000L, level = 0.42f, speechDetected = true)
        controller.update(recording)
        assertBubble(bubble, recording, "Listening; tap to stop")

        val processing = DictationState.Processing("Transcribing\u2026")
        controller.update(processing)
        assertBubble(bubble, processing, "Transcribing\u2026")

        val success = DictationState.Success("Inserted")
        controller.update(success)
        assertBubble(bubble, success, "Inserted")

        val error = DictationState.Error("Engine failed to load")
        controller.update(error)
        assertBubble(bubble, error, "Error: Engine failed to load")

        controller.update(DictationState.Idle)
        assertBubble(bubble, DictationState.Idle, "Tap to dictate")

        assertEquals(1, bubbleViews(windowManager).size)
        assertSame(bubble, bubbleViews(windowManager).single())

        controller.hide()
        assertTrue(bubbleViews(windowManager).isEmpty())
        assertFalse(controller.isShowing)
    }

    @Test
    fun `show plus every state update keeps exactly one registered bubble`() {
        val controller = OverlayController(context)
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        assertTrue(controller.show(listener))
        assertEquals(1, bubbleViews(windowManager).size)
        val bubble = bubbleViews(windowManager).single()

        val states = listOf(
            DictationState.Idle,
            DictationState.Loading(),
            DictationState.Recording(0L, 0f, false),
            DictationState.Processing(),
            DictationState.Success("Inserted"),
            DictationState.Error("nope"),
            DictationState.Idle,
            DictationState.Idle,
        )
        states.forEach { state ->
            controller.update(state)
            assertEquals(1, bubbleViews(windowManager).size)
            assertSame(bubble, bubbleViews(windowManager).single())
            assertEquals(state, bubble.state)
        }

        controller.hide()
        assertTrue(bubbleViews(windowManager).isEmpty())
    }

    @Test
    fun `state changes never resize or move the bubble under the finger`() {
        val controller = OverlayController(context)
        assertTrue(controller.show(listener))
        controller.update(DictationState.Idle)
        val before = controller.positionForTest()
        val sizeBefore = controller.sizeForTest()

        controller.update(DictationState.Loading("Loading\u2026"))
        controller.update(DictationState.Recording(1_000L, 0.4f, true))
        controller.update(DictationState.Processing())
        controller.update(DictationState.Success("Inserted"))
        controller.update(DictationState.Error("boom"))

        assertEquals(before, controller.positionForTest())
        assertEquals(sizeBefore, controller.sizeForTest())
        controller.hide()
    }

    private fun assertBubble(bubble: MicBubbleView, state: DictationState, description: String) {
        assertEquals(state, bubble.state)
        assertEquals(description, bubble.contentDescription)
    }

    private fun bubbleViews(windowManager: WindowManager): List<MicBubbleView> =
        (shadowOf(windowManager) as ShadowWindowManagerImpl).views.filterIsInstance<MicBubbleView>()
}
