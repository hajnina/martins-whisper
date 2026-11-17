package com.martins.whisper.harness

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
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
import kotlin.math.PI
import kotlin.math.sin

/**
 * Guards the capture -> orb voice feed at the service level: the [DictationService] audio
 * callback must run every captured frame through the real
 * [com.martins.whisper.core.voice.VoiceFeatureExtractor] and hand the result to the bubble.
 *
 * The measured-features branch is the only path that moves the orb's hue off its neutral 0.5 —
 * the state-level fallback passes no pitch. So a 440 Hz tone recorded through the real service
 * must end with the orb hue following the measured pitch. Mutation: delete the
 * `overlay.onVoiceFeatures(voiceExtractor.process(...))` line in [DictationService] -> the hue
 * stays ~0.5 and this test fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class DictationServiceVoiceFeedTest {

    private lateinit var app: Application
    private lateinit var source: SpeechToneSource
    private lateinit var originalEngine: (Context, SettingsStore) -> SpeechEngine
    private lateinit var originalInserter: (Context) -> TextInserter
    private lateinit var originalAudio: (Context) -> AudioSource

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        ShadowSettings.setCanDrawOverlays(true)
        originalEngine = EngineFactory.factory
        originalInserter = DictationService.inserterFactory
        originalAudio = DictationService.audioSourceFactory
        source = SpeechToneSource()
        DictationService.audioSourceFactory = { source }
        EngineFactory.factory = { _, _ ->
            FakeSpeechEngine(
                backend = "voice-feed-fake",
                respond = { _, language -> Transcript(text = "", language = language ?: "en") },
            )
        }
        DictationService.inserterFactory = {
            object : TextInserter {
                override fun insert(text: String): InsertResult = InsertResult.Inserted
            }
        }
    }

    @After
    fun tearDown() {
        EngineFactory.factory = originalEngine
        DictationService.inserterFactory = originalInserter
        DictationService.audioSourceFactory = originalAudio
    }

    @Test
    fun `captured speech frames reach the bubble as measured features`() {
        val intent = Intent(app, DictationService::class.java).setAction(DictationService.ACTION_START)
        val controller = Robolectric.buildService(DictationService::class.java, intent)
            .create()
            .startCommand(0, 0)
        try {
            val bubble = bubbleView()
            val downAt = SystemClock.uptimeMillis()
            dispatch(bubble, MotionEvent.ACTION_DOWN, downAt)
            dispatch(bubble, MotionEvent.ACTION_UP, downAt + TAP_MS)

            await("recording starts on the tap toggle") {
                DictationService.state.value is DictationState.Recording
            }
            await("enough tone frames flow through the capture callback") {
                source.frames.get() >= MIN_TONE_FRAMES
            }
            repeat(DRAW_FRAMES) {
                shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
                bubble.draw(Canvas(Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)))
            }
            val level = bubble.ribbonLevelForTest()
            assertTrue(
                "ribbon level=$level must follow the measured speech (the fallback path " +
                    "passes no features, so it stays near the state level)",
                level > MEASURED_LEVEL,
            )
        } finally {
            controller.destroy()
        }
    }

    private fun bubbleView(): MicBubbleView {
        val windowManager = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val views = (shadowOf(windowManager) as ShadowWindowManagerImpl).views
            .filterIsInstance<MicBubbleView>()
        assertTrue("overlay bubble must be attached for the voice-feed test", views.isNotEmpty())
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
            "$what; state=${DictationService.state.value} frames=${source.frames.get()}",
        )
    }

    /**
     * The dead-source probe window is silent, then a continuous 440 Hz sine at speech level;
     * per-frame index keeps the phase continuous across frame boundaries.
     */
    private class SpeechToneSource : AudioSource {
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
                            ShortArray(FRAME_SAMPLES) { sample ->
                                val position = index * FRAME_SAMPLES + sample
                                (SPEECH_LEVEL * sin(2.0 * PI * PITCH_HZ * position / SAMPLE_RATE))
                                    .toInt()
                                    .toShort()
                            }
                        }
                        onFrame(frame, SystemClock.elapsedRealtime())
                        frames.incrementAndGet()
                        index++
                        Thread.sleep(FRAME_MS)
                    }
                } catch (_: InterruptedException) {
                }
            }, "voice-feed-audio").apply { isDaemon = true }
            thread?.start()
        }

        override fun stop() {
            running.set(false)
            thread?.join(500)
            thread = null
        }

        override fun release() = stop()
    }

    private companion object {
        const val TAP_MS = 12L
        const val SIZE_PX = 168
        const val FRAME_SAMPLES = 320
        const val FRAME_MS = 20L
        const val PROBE_FRAMES = 15
        const val SAMPLE_RATE = 16_000
        const val PITCH_HZ = 440.0
        const val SPEECH_LEVEL = 6_000.0
        const val MIN_TONE_FRAMES = 45
        const val DRAW_FRAMES = 30
        const val MEASURED_LEVEL = 0.3f
    }
}
