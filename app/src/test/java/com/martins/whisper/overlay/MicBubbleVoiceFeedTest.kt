package com.martins.whisper.overlay

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import com.martins.whisper.core.voice.VoiceFeatures
import com.martins.whisper.service.DictationState
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class MicBubbleVoiceFeedTest {

    private fun bubble(recordingLevel: Float): MicBubbleView {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = MicBubbleView(activity)
        activity.setContentView(view)
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(168, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(168, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 168, 168)
        view.state = DictationState.Recording(elapsedMs = 500, level = recordingLevel, speechDetected = true)
        shadowOf(Looper.getMainLooper()).idle()
        return view
    }

    private fun settleAndDraw(view: MicBubbleView) {
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            view.draw(Canvas(Bitmap.createBitmap(168, 168, Bitmap.Config.ARGB_8888)))
        }
    }

    private fun features(pitchNorm: Float, brightness: Float) = VoiceFeatures(
        level = 0.6f,
        peakLevel = 0.7f,
        pitchHz = 220f,
        pitchNorm = pitchNorm,
        voicedProb = 1f,
        brightness = brightness,
        onset = false,
        timestampMs = 1L,
    )

    @Test
    fun `bubble follows measured pitch and brightness when the service feeds them`() {
        val view = bubble(recordingLevel = 0.1f)
        view.onVoiceFeatures(features(pitchNorm = 0.95f, brightness = 1f))
        settleAndDraw(view)

        val motion = view.lastOrbMotion
        assertNotNull(motion)
        assertTrue("hue=${motion!!.hue}", motion.hue > 0.75f)
        assertTrue("edge=${motion.edgeCrispness}", motion.edgeCrispness > 0.7f)
        assertTrue("lobes=${motion.lobes}", motion.lobes >= 6)
    }

    @Test
    fun `without features the bubble falls back to the measured recording level`() {
        val quiet = bubble(recordingLevel = 0.1f)
        val loud = bubble(recordingLevel = 0.9f)
        settleAndDraw(quiet)
        settleAndDraw(loud)
        assertTrue(
            "quiet=${quiet.lastOrbMotion!!.radius} loud=${loud.lastOrbMotion!!.radius}",
            loud.lastOrbMotion!!.radius > quiet.lastOrbMotion!!.radius + 0.3f,
        )
    }
}
