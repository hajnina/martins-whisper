package com.martins.whisper.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import com.martins.whisper.core.voice.VoiceFeatures
import com.martins.whisper.service.DictationState
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/**
 * Test-only renderer for the Precision Instrument bubble. Draws the real view at 72 dp (216 px)
 * and the 180x72 recording pill (540x216 px) so the result can be compared to the design sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InstrumentRenderTest {

    private val output = File(
        System.getProperty("instrument.render.dir") ?: "/tmp/opencode/design/impl-grille/render",
    )

    @Test
    fun `render every instrument state`() {
        output.mkdirs()
        val states = listOf(
            "idle" to DictationState.Idle,
            "recording" to DictationState.Recording(2_400, 0.62f, true),
            "processing" to DictationState.Processing(),
            "success" to DictationState.Success("Inserted"),
            "error" to DictationState.Error("No microphone"),
        )
        for ((name, state) in states) {
            val bubble = MicBubbleView(RuntimeEnvironment.getApplication())
            bubble.hapticFeedback = {}
            bubble.scheduleHaptic = { _, action -> action() }
            bubble.state = state
            bubble.onVoiceFeatures(
                VoiceFeatures(
                    level = 0.62f,
                    peakLevel = 0.7f,
                    pitchHz = 180f,
                    pitchNorm = 0.4f,
                    voicedProb = 0.9f,
                    brightness = 0.5f,
                    onset = false,
                    timestampMs = 0L,
                ),
            )
            bubble.settleMotionForTest()
            if (state is DictationState.Recording) {
                render(bubble, 216, 540, "recording_pill_540x216.png")
                render(bubble, 72, 72, "recording_compact_72.png")
            } else {
                render(bubble, 216, 216, "${name}_216.png")
            }
        }
        assertTrue(File(output, "idle_216.png").length() > 0)
    }

    private fun render(view: MicBubbleView, widthDp: Int, heightDp: Int, name: String) {
        val density = view.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        view.settleMotionForTest()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // The design sheet shows the bubble on a neutral paper field; keep the alpha visible.
        canvas.drawColor(Color.TRANSPARENT)
        view.draw(canvas)
        FileOutputStream(File(output, name)).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
    }
}
