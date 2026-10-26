package com.martins.whisper.ui.orb

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import com.martins.whisper.core.voice.VoiceFeatures
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
class SoundOrbViewTest {

    private fun view(): SoundOrbView {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val created = SoundOrbView(activity)
        activity.setContentView(created)
        created.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
        )
        created.layout(0, 0, 300, 300)
        shadowOf(Looper.getMainLooper()).idle()
        return created
    }

    private fun draw(view: SoundOrbView) {
        view.draw(Canvas(Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)))
    }

    private fun settle(view: SoundOrbView) {
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            draw(view)
        }
    }

    private fun features(
        level: Float,
        pitchNorm: Float? = null,
        voiced: Float = 1f,
        brightness: Float = 0.5f,
        onset: Boolean = false,
    ) = VoiceFeatures(
        level = level,
        peakLevel = (level + 0.05f).coerceAtMost(1f),
        pitchHz = if (pitchNorm == null) null else 220f,
        pitchNorm = pitchNorm ?: 0f,
        voicedProb = voiced,
        brightness = brightness,
        onset = onset,
        timestampMs = 1L,
    )

    @Test
    fun `idle orb draws a static frame with no ticker`() {
        val view = view()
        draw(view)
        assertNotNull(view.lastMotion)
        assertTrue(view.lastMotion!!.radius < 0.05f)
    }

    @Test
    fun `measured level expands the orb`() {
        val loud = view().also { it.onVoiceFeatures(features(0.9f)) }
        val quiet = view().also { it.onVoiceFeatures(features(0.05f)) }
        settle(loud)
        settle(quiet)
        assertTrue("loud=${loud.lastMotion!!.radius}", loud.lastMotion!!.radius > quiet.lastMotion!!.radius + 0.3f)
    }

    @Test
    fun `measured pitch moves the hue from warm to cool`() {
        val low = view().also { it.onVoiceFeatures(features(0.4f, pitchNorm = 0.05f)) }
        val high = view().also { it.onVoiceFeatures(features(0.4f, pitchNorm = 0.95f)) }
        settle(low)
        settle(high)
        assertTrue("low=${low.lastMotion!!.hue}", low.lastMotion!!.hue < 0.25f)
        assertTrue("high=${high.lastMotion!!.hue}", high.lastMotion!!.hue > 0.75f)
    }

    @Test
    fun `measured brightness sharpens the edge and adds lobes`() {
        val dark = view().also { it.onVoiceFeatures(features(0.4f, brightness = 0f)) }
        val bright = view().also { it.onVoiceFeatures(features(0.4f, brightness = 1f)) }
        settle(dark)
        settle(bright)
        assertTrue("dark edge=${dark.lastMotion!!.edgeCrispness}", dark.lastMotion!!.edgeCrispness < 0.3f)
        assertTrue("bright edge=${bright.lastMotion!!.edgeCrispness}", bright.lastMotion!!.edgeCrispness > 0.7f)
        assertTrue(dark.lastMotion!!.lobes < bright.lastMotion!!.lobes)
    }

    @Test
    fun `unvoiced frame adds grain and does not invent a pitch`() {
        val view = view().also { it.onVoiceFeatures(features(0.5f, pitchNorm = null, voiced = 0f)) }
        settle(view)
        assertTrue("wobble=${view.lastMotion!!.wobble}", view.lastMotion!!.wobble > 0.2f)
    }
}
