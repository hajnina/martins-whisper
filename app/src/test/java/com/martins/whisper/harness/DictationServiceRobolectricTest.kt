package com.martins.whisper.harness

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import com.martins.whisper.audio.AudioSource
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.Transcript
import com.martins.whisper.core.asr.FakeSpeechEngine
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.service.DictationService
import com.martins.whisper.service.DictationState
import com.martins.whisper.service.EngineFactory
import com.martins.whisper.service.InsertResult
import com.martins.whisper.service.TextInserter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Layer 1 Robolectric end-to-end: the real service state machine is driven with a fake
 * microphone and the fake SpeechEngine, then the transcript is inserted through the injectable
 * TextInserter. No AudioRecord, no model file, no device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class DictationServiceRobolectricTest {

    private lateinit var app: Application
    private lateinit var engine: FakeSpeechEngine
    private lateinit var audio: FakeAudioSource
    private var inserted: String? = null
    private lateinit var originalFactory: (Context, SettingsStore) -> SpeechEngine
    private lateinit var originalInserterFactory: (Context) -> TextInserter
    private lateinit var originalAudioSourceFactory: (Context) -> AudioSource

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        engine = FakeSpeechEngine(
            backend = "fake-jvm",
            respond = { _, language ->
                Transcript(text = "hello from fake", language = language)
            },
        )
        audio = FakeAudioSource()
        inserted = null
        originalFactory = EngineFactory.factory
        originalInserterFactory = DictationService.inserterFactory
        originalAudioSourceFactory = DictationService.audioSourceFactory
        EngineFactory.factory = { _, _ -> engine }
        DictationService.inserterFactory = {
            object : TextInserter {
                override fun insert(text: String): InsertResult {
                    inserted = text
                    return InsertResult.Inserted
                }
            }
        }
        DictationService.audioSourceFactory = { audio }
    }

    @After
    fun tearDown() {
        EngineFactory.factory = originalFactory
        DictationService.inserterFactory = originalInserterFactory
        DictationService.audioSourceFactory = originalAudioSourceFactory
    }

    @Test
    fun `press record transcribe insert and idle unload run end to end`() {
        val controller = Robolectric.buildService(DictationService::class.java).create()
        val service = controller.get()

        invoke(service, "onPress")
        await("recording starts") { audio.started && DictationService.state.value is DictationState.Recording }
        assertTrue(engine.received.isEmpty())

        invoke(service, "onRelease")
        await("transcript is inserted") { inserted != null }

        assertEquals("hello from fake", inserted)
        assertTrue(DictationService.state.value is DictationState.Success)
        assertEquals(1, engine.received.size)
        assertEquals(FakeAudioSource.FRAMES * FakeAudioSource.FRAME_SAMPLES * 1000L / 16_000, engine.received[0].chunk.endMs)

        val broadcast = shadowOf(app).broadcastIntents.last { it.action == DictationService.ACTION_TRANSCRIPT }
        assertEquals("hello from fake", broadcast.getStringExtra(DictationService.EXTRA_TRANSCRIPT))

        shadowOf(Looper.getMainLooper()).idleFor(301, TimeUnit.SECONDS)
        assertTrue(engine.closed)

        controller.destroy()
        assertEquals(DictationState.Idle, DictationService.state.value)
    }

    @Test
    fun `start command enters foreground and stop action stops the service`() {
        val startIntent = Intent(app, DictationService::class.java)
        val start = Robolectric.buildService(DictationService::class.java, startIntent)
            .create()
            .startCommand(0, 0)
        assertNotNull(shadowOf(start.get()).lastForegroundNotification)
        assertEquals(DictationState.Idle, DictationService.state.value)
        start.destroy()

        val stopIntent = Intent(app, DictationService::class.java)
            .setAction(DictationService.ACTION_STOP)
        val stop = Robolectric.buildService(DictationService::class.java, stopIntent)
            .create()
            .startCommand(0, 0)
        assertTrue(shadowOf(stop.get()).isStoppedBySelf)
    }

    private fun invoke(service: DictationService, method: String) {
        DictationService::class.java
            .getDeclaredMethod(method)
            .apply { isAccessible = true }
            .invoke(service)
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
        throw AssertionError("timed out waiting for $what; state=${DictationService.state.value}")
    }

    private class FakeAudioSource : AudioSource {
        var started = false
            private set

        override fun start(onFrame: (ShortArray, Long) -> Unit, onError: (Throwable) -> Unit) {
            started = true
            var time = 1_000L
            repeat(FRAMES) {
                onFrame(TONE, time)
                time += 20
            }
        }

        override fun stop() = Unit
        override fun release() = Unit

        companion object {
            const val FRAMES = 60
            const val FRAME_SAMPLES = 320
            val TONE = ShortArray(FRAME_SAMPLES) { 6_000.toShort() }
        }
    }

}
