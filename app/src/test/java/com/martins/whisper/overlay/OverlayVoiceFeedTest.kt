package com.martins.whisper.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import com.martins.whisper.core.voice.VoiceFeatureExtractor
import com.martins.whisper.service.DictationState
import kotlin.math.PI
import kotlin.math.sin
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
import java.util.concurrent.TimeUnit

/**
 * The service seam end to end: the real [VoiceFeatureExtractor] turns capture frames into
 * features, [OverlayController] hands them to the bubble, and the orb follows the measured
 * motion instead of the coarse state level.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class OverlayVoiceFeedTest {

    private lateinit var controller: OverlayController
    private lateinit var bubble: MicBubbleView

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(true)
        controller = OverlayController(context)
        assertTrue(controller.show(overlayListener))
        bubble = controller.bubbleForTest()!!
        bubble.measure(
            View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY),
        )
        bubble.layout(0, 0, SIZE_PX, SIZE_PX)
    }

    @Test
    fun `extractor frames fed through the controller drive the ribbon`() {
        bubble.state = DictationState.Recording(
            elapsedMs = 500,
            level = QUIET_STATE_LEVEL,
            speechDetected = true,
        )
        settleAndDraw()
        val fallback = bubble.ribbonLevelForTest()

        val extractor = VoiceFeatureExtractor()
        repeat(FRAMES_FED) { frameIndex ->
            val frame = ShortArray(FRAME_SAMPLES) { index ->
                val sample = frameIndex * FRAME_SAMPLES + index
                (SPEECH_LEVEL * sin(2.0 * PI * PITCH_HZ * sample / SAMPLE_RATE)).toInt().toShort()
            }
            controller.onVoiceFeatures(extractor.process(frame, frameIndex * FRAME_MS))
        }
        shadowOf(Looper.getMainLooper()).idle()
        settleAndDraw()

        val measured = bubble.ribbonLevelForTest()
        assertTrue(
            "fallback=$fallback measured=$measured",
            measured > fallback + 0.2f,
        )
    }

    private fun settleAndDraw() {
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            bubble.draw(Canvas(Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)))
        }
    }

    private val overlayListener = object : MicBubbleView.Listener {
        override fun onPress() = Unit
        override fun onRelease() = Unit
        override fun onCancel() = Unit
        override fun onDrag(dx: Float, dy: Float) = Unit
        override fun onDragEnd() = Unit
        override fun onSettingsTap() = Unit
        override fun onStopTap() = Unit
    }

    private companion object {
        const val SIZE_PX = 168
        const val FRAME_SAMPLES = 320
        const val FRAME_MS = 20L
        const val FRAMES_FED = 40
        const val SAMPLE_RATE = 16_000
        const val PITCH_HZ = 440.0
        const val SPEECH_LEVEL = 6_000.0
        const val QUIET_STATE_LEVEL = 0.05f
    }
}
