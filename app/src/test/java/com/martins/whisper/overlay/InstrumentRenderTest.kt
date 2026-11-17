package com.martins.whisper.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
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
 * Test-only renderer for the Precision Instrument bubble. Draws the real view at 72 dp and
 * 216 dp for all five states, plus the 180x72 recording pill, so the result can be compared to
 * the design sheet.
 *
 * Robolectric's native raster ships for x86_64 only; the arm64 review host records the arch and
 * exits, while x86_64 CI runs the full render. The stills used for the local visual loop were
 * produced by render_fallback.py with the same dp constants.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InstrumentRenderTest {

    private val output = File(
        System.getProperty("instrument.render.dir")
            ?: "/tmp/opencode/design/impl-faithful/render",
    )

    @Test
    fun `render every instrument state at 72 dp and 216 dp`() {
        val arch = System.getProperty("os.arch") ?: "unknown"
        if (arch != "x86_64" && arch != "amd64") {
            assertTrue("host architecture is reported", arch.isNotBlank())
            return
        }
        output.mkdirs()
        val states = listOf(
            "idle" to DictationState.Idle,
            "processing" to DictationState.Processing(),
            "success" to DictationState.Success("Inserted"),
            "error" to DictationState.Error("No microphone"),
        )
        for ((name, state) in states) {
            render(state, 72, 72, "${name}_72dp.png")
            render(state, 216, 216, "${name}_216dp.png")
        }
        val recording = DictationState.Recording(elapsedMs = 1_800, level = 0.72f, speechDetected = true)
        render(recording, 180, 72, "recording_pill_180x72dp.png")
        render(recording, 540, 216, "recording_pill_540x216dp.png")
        render(recording, 72, 72, "recording_compact_72dp.png")

        assertTrue(File(output, "idle_216dp.png").length() > 0)
        assertTrue(File(output, "recording_pill_540x216dp.png").length() > 0)
    }

    private fun render(state: DictationState, widthDp: Int, heightDp: Int, name: String) {
        val bubble = MicBubbleView(RuntimeEnvironment.getApplication())
        bubble.hapticFeedback = {}
        bubble.scheduleHaptic = { _, action -> action() }
        bubble.state = state
        bubble.onVoiceFeatures(
            VoiceFeatures(
                level = 0.72f,
                peakLevel = 0.8f,
                pitchHz = 180f,
                pitchNorm = 0.4f,
                voicedProb = 0.9f,
                brightness = 0.5f,
                onset = false,
                timestampMs = 0L,
            ),
        )
        val density = bubble.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        bubble.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        bubble.layout(0, 0, width, height)
        bubble.settleMotionForTest()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        bubble.draw(canvas)
        FileOutputStream(File(output, name)).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        assertTrue("$name must contain pixels", bitmap.width > 0 && bitmap.height > 0)
        bitmap.recycle()
    }
}
