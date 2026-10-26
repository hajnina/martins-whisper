package com.martins.whisper.harness

import android.content.Context
import android.view.WindowManager
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.overlay.MicBubbleView
import com.martins.whisper.overlay.OverlayController
import com.martins.whisper.service.DictationState
import org.junit.Assert.assertFalse
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class OverlayControllerRobolectricTest {

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
    fun `overlay attaches survives expanded states drags and hides`() {
        val controller = OverlayController(context)
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        assertTrue(controller.show(listener))
        assertTrue(overlayViews(windowManager).isNotEmpty())

        controller.update(DictationState.Loading())
        controller.update(DictationState.Recording(1_000, 0.4f, true))
        controller.update(DictationState.Processing())
        controller.update(DictationState.Success("Inserted"))
        controller.update(DictationState.Error("nope"))
        controller.update(DictationState.Idle)
        controller.dragBy(-10_000f, -10_000f)
        controller.persistPosition()

        val settings = SettingsStore(context)
        assertTrue(settings.hasSavedBubblePosition())

        controller.hide()
        assertTrue(overlayViews(windowManager).isEmpty())
    }

    @Test
    fun `overlay refuses to show without the draw-overlays permission`() {
        ShadowSettings.setCanDrawOverlays(false)
        val controller = OverlayController(context)

        assertFalse(controller.show(listener))
        assertFalse(controller.isShowing)
    }

    private fun overlayViews(windowManager: WindowManager): List<MicBubbleView> =
        (shadowOf(windowManager) as ShadowWindowManagerImpl).views.filterIsInstance<MicBubbleView>()
}
