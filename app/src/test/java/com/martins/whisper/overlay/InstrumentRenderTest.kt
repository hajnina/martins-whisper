package com.martins.whisper.overlay

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import com.martins.whisper.service.DictationState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Renders the real bubble into PNGs for the design review loop. Test-only; the output directory is
 * outside the APK and is recreated on every run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InstrumentRenderTest {

    private val output = File(
        System.getenv("DESIGN_RENDER_DIR")
            ?: "/tmp/opencode/design/impl-faithful/render",
    )

    @Test
    @Config(qualifiers = "mdpi")
    fun `render every state at 72 px`() {
        assumeNativeGraphics()
        renderSet(densityLabel = "72", discPx = 72, pillPx = 180, suffix = "_72")
    }

    @Test
    @Config(qualifiers = "xxhdpi")
    fun `render every state at 216 px`() {
        assumeNativeGraphics()
        renderSet(densityLabel = "216", discPx = 216, pillPx = 540, suffix = "_216")
    }

    /** Robolectric's native raster ships for x86_64 Linux/macOS only; arm hosts skip the loop. */
    private fun assumeNativeGraphics() {
        val arch = System.getProperty("os.arch") ?: ""
        org.junit.Assume.assumeTrue(
            "Robolectric native graphics is unavailable on os.arch=$arch",
            arch == "x86_64" || arch == "amd64",
        )
    }

    private fun renderSet(densityLabel: String, discPx: Int, pillPx: Int, suffix: String) {
        RuntimeEnvironment.setQualifiers(if (densityLabel == "72") "mdpi" else "xxhdpi")
        output.mkdirs()
        val states = listOf(
            "idle" to DictationState.Idle,
            "processing" to DictationState.Processing(),
            "success" to DictationState.Success("Inserted"),
            "error" to DictationState.Error("No microphone"),
        )
        for ((name, state) in states) {
            val bitmap = render(state, discPx, discPx)
            write(bitmap, "$name$suffix.png")
        }
        val recording = render(
            DictationState.Recording(elapsedMs = 900, level = 0.72f, speechDetected = true),
            pillPx,
            discPx,
        )
        write(recording, "recording_pill_${pillPx}x${discPx}.png")
        val compact = render(
            DictationState.Recording(elapsedMs = 900, level = 0.72f, speechDetected = true),
            discPx,
            discPx,
        )
        write(compact, "recording$suffix.png")
        assertTrue("no PNGs written", output.listFiles()?.isNotEmpty() == true)
    }

    private fun render(state: DictationState, width: Int, height: Int): Bitmap {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = MicBubbleView(activity)
        activity.setContentView(view)
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        view.state = state
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        repeat(90) {
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            view.draw(canvas)
        }
        return bitmap
    }

    private fun write(bitmap: Bitmap, name: String) {
        FileOutputStream(File(output, name)).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
    }
}
