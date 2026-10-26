package com.martins.whisper.harness

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import com.martins.whisper.audio.AudioSource
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.Transcript
import com.martins.whisper.core.asr.FakeSpeechEngine
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.overlay.MicBubbleView
import com.martins.whisper.service.DictationService
import com.martins.whisper.service.DictationState
import com.martins.whisper.service.EngineFactory
import com.martins.whisper.service.InsertResult
import com.martins.whisper.service.TextInserter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * Reproduces DEVICE-REPORT #2 on the host: POCO F8 Pro, build 4fd07e6, `adb shell input tap`
 * on the bubble -> AudioRecord session lives ~12 ms, model loads, then NOTHING (no capture, no
 * transcription, no insert).
 *
 * The real [DictationService] and the real [MicBubbleView] touch handling run here. Only the
 * microphone (a 16 kHz source that emits the silent dead-source probe and then speech) and the
 * model loader (blocked until the test releases it, like the ~0.1-1 s real load) are faked.
 *
 * Product requirement (MFVP.md, README): tap the bubble -> speak -> tap again. So the first
 * tap MUST leave capture running while the model loads, and speech frames MUST keep arriving
 * after the load. Regression guard: the shipped B2 fix made the bubble tap-to-toggle
 * (06b7db6); on the pre-fix push-to-talk bubble ACTION_UP landed 12 ms after ACTION_DOWN,
 * the hold was < MIN_HOLD_MS, the session was cancelled and nothing was captured after the
 * model load. Mutation: make ACTION_UP call onRelease regardless of the latching state again
 * -> this test fails with `state=Idle, running=false` after the load.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class TapToDictateReproTest {

    private lateinit var app: Application
    private lateinit var source: ProbeThenSpeechSource
    private lateinit var originalEngine: (Context, SettingsStore) -> SpeechEngine
    private lateinit var originalInserter: (Context) -> TextInserter
    private lateinit var originalAudio: (Context) -> AudioSource

    private val loadEntered = CountDownLatch(1)
    private val releaseLoad = CountDownLatch(1)
    private val inserted = AtomicBoolean(false)

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        ShadowSettings.setCanDrawOverlays(true)
        originalEngine = EngineFactory.factory
        originalInserter = DictationService.inserterFactory
        originalAudio = DictationService.audioSourceFactory

        source = ProbeThenSpeechSource()
        DictationService.audioSourceFactory = { source }
        EngineFactory.factory = { _, _ ->
            loadEntered.countDown()
            check(releaseLoad.await(10, TimeUnit.SECONDS)) { "test never released the model load" }
            FakeSpeechEngine(
                backend = "repro-fake",
                respond = { _, language -> Transcript(text = "hello from tap", language = language ?: "en") },
            )
        }
        DictationService.inserterFactory = {
            object : TextInserter {
                override fun insert(text: String): InsertResult {
                    inserted.set(text == "hello from tap")
                    return InsertResult.Inserted
                }
            }
        }
    }

    @After
    fun tearDown() {
        releaseLoad.countDown()
        EngineFactory.factory = originalEngine
        DictationService.inserterFactory = originalInserter
        DictationService.audioSourceFactory = originalAudio
    }

    @Test
    fun `an adb style tap keeps capture running through the model load`() {
        val intent = Intent(app, DictationService::class.java).setAction(DictationService.ACTION_START)
        val controller = Robolectric.buildService(DictationService::class.java, intent)
            .create()
            .startCommand(0, 0)
        try {
            val bubble = bubbleView()
            val downAt = SystemClock.uptimeMillis()
            dispatch(bubble, MotionEvent.ACTION_DOWN, downAt)
            dispatch(bubble, MotionEvent.ACTION_UP, downAt + TAP_MS)

            await("the dead-source probe / capture starts on ACTION_DOWN") { source.started.get() }
            assertTrue("model load must be in flight (prepare/load on a background dispatcher)", loadEntered.await(5, TimeUnit.SECONDS))

            val framesAtLoad = source.frames.get()
            releaseLoad.countDown()

            await("recording continues after the model load (tap-to-toggle)") {
                DictationService.state.value is DictationState.Recording
            }
            await("capture keeps delivering frames after the load (nothing may stop after the probe)") {
                source.frames.get() >= framesAtLoad + 3
            }
            assertTrue("the fake AudioSource must still be open", source.running.get())
        } finally {
            releaseLoad.countDown()
            controller.destroy()
        }
    }

    private fun bubbleView(): MicBubbleView {
        val windowManager = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val views = (shadowOf(windowManager) as ShadowWindowManagerImpl).views
            .filterIsInstance<MicBubbleView>()
        assertTrue("overlay bubble must be attached for the tap test", views.isNotEmpty())
        return views.single()
    }

    private fun dispatch(view: MicBubbleView, action: Int, at: Long) {
        val event = MotionEvent.obtain(at, at, action, view.width / 2f, view.height / 2f, 0)
        event.setLocation(view.width / 2f, view.height / 2f)
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun await(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) {
                shadowOf(Looper.getMainLooper()).idle()
                return
            }
            Thread.sleep(10)
        }
        throw AssertionError(
            "$what; state=${DictationService.state.value} source(started=${source.started.get()}," +
                " running=${source.running.get()}, frames=${source.frames.get()}) inserted=${inserted.get()}",
        )
    }

    /**
     * 20 ms frames like AudioRecordSource: the first [PROBE_FRAMES] are digital silence (the
     * dead-source probe window), then a speech-level tone. stop() closes it immediately, which
     * is what the phone's ~12 ms AudioRecord session did on the aborted tap.
     */
    private class ProbeThenSpeechSource : AudioSource {
        val started = AtomicBoolean(false)
        val running = AtomicBoolean(false)
        val frames = AtomicLong(0)
        private var thread: Thread? = null

        override fun start(onFrame: (ShortArray, Long) -> Unit, onError: (Throwable) -> Unit) {
            if (!running.compareAndSet(false, true)) return
            started.set(true)
            thread = Thread({
                var index = 0
                try {
                    while (running.get()) {
                        val frame = if (index < PROBE_FRAMES) {
                            ShortArray(FRAME_SAMPLES)
                        } else {
                            ShortArray(FRAME_SAMPLES) { SPEECH_LEVEL }
                        }
                        onFrame(frame, SystemClock.elapsedRealtime())
                        frames.incrementAndGet()
                        index++
                        Thread.sleep(FRAME_MS)
                    }
                } catch (_: InterruptedException) {
                }
            }, "repro-audio").apply { isDaemon = true }
            thread?.start()
        }

        override fun stop() {
            running.set(false)
            thread?.join(500)
            thread = null
        }

        override fun release() = stop()

        companion object {
            const val FRAME_SAMPLES = 320
            const val FRAME_MS = 20L
            const val PROBE_FRAMES = 15
            const val SPEECH_LEVEL = 6_000.toShort()
        }
    }

    private companion object {
        const val TAP_MS = 12L
    }
}
