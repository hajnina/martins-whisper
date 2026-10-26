package com.martins.whisper.harness

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Looper
import com.martins.whisper.audio.AudioSource
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.Transcript
import com.martins.whisper.core.TranscriptGate
import com.martins.whisper.core.asr.FakeSpeechEngine
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.service.DictationService
import com.martins.whisper.service.DictationState
import com.martins.whisper.service.EngineFactory
import com.martins.whisper.service.InsertResult
import com.martins.whisper.service.TextInserter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.shadows.ShadowSystemClock

/**
 * Service-layer Robolectric coverage for the G2 path: capture starts while the engine is still
 * loading, the 300 s idle unload reloads lazily, cancel during a blocked load is harmless, and a
 * model switch closes the previous engine. The audio source is a manual fake so frames can be
 * emitted exactly while a latch blocks the engine loader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class DictationServiceLifecycleRobolectricTest {

    private lateinit var app: Application
    private lateinit var settings: SettingsStore
    private lateinit var audio: ManualAudioSource
    private val inserted = CopyOnWriteArrayList<String>()
    private lateinit var originalFactory: (Context, SettingsStore) -> SpeechEngine
    private lateinit var originalInserterFactory: (Context) -> TextInserter
    private lateinit var originalAudioSourceFactory: (Context) -> AudioSource
    private lateinit var originalTargetCapture: () -> Unit

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        settings = SettingsStore(app)
        settings.modelId = SettingsStore.DEFAULT_MODEL_ID
        settings.customModels = emptyList()
        settings.idleUnloadSeconds = SettingsStore.DEFAULT_IDLE_UNLOAD_SECONDS

        inserted.clear()
        audio = ManualAudioSource()

        originalFactory = EngineFactory.factory
        originalInserterFactory = DictationService.inserterFactory
        originalAudioSourceFactory = DictationService.audioSourceFactory
        originalTargetCapture = DictationService.targetCapture
        DictationService.targetCapture = {}

        EngineFactory.factory = { _, _ ->
            FakeSpeechEngine(respond = { _, language -> Transcript("hello from fake", language) })
        }
        DictationService.inserterFactory = {
            object : TextInserter {
                override fun insert(text: String): InsertResult {
                    inserted += text
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
        DictationService.targetCapture = originalTargetCapture
    }

    @Test
    fun `frames captured while the model loads are buffered in order and inserted once`() {
        val loaderEntered = CountDownLatch(1)
        val releaseLoader = CountDownLatch(1)
        val engine = FakeSpeechEngine(
            respond = { _, language -> Transcript("hello from fake", language) },
        )
        var created = 0
        EngineFactory.factory = { _, _ ->
            created += 1
            loaderEntered.countDown()
            assertTrue(releaseLoader.await(5, TimeUnit.SECONDS))
            engine
        }
        val controller = Robolectric.buildService(DictationService::class.java).create()
        val service = controller.get()
        try {
            invoke(service, "onPress")
            assertTrue("audio capture starts before the engine finishes loading", audio.started)
            assertTrue("loader should be blocked", loaderEntered.await(2, TimeUnit.SECONDS))
            assertTrue(DictationService.state.value is DictationState.Loading)

            audio.emit(frames = 10, firstSample = 100, step = 100)
            assertTrue(
                "frames must not wait for the model",
                DictationService.state.value is DictationState.Loading,
            )

            releaseLoader.countDown()
            await("recording state after the load completes") {
                DictationService.state.value is DictationState.Recording
            }

            invoke(service, "onRelease")
            await("transcript is inserted") { inserted.size == 1 }

            assertEquals(listOf("hello from fake"), inserted.toList())
            assertTrue(DictationService.state.value is DictationState.Success)
            assertEquals(1, engine.received.size)
            val chunk = engine.received.single().chunk
            assertEquals(10 * ManualAudioSource.FRAME_SAMPLES, chunk.samples.size)
            assertEquals(200L, chunk.endMs)
            chunk.samples.forEachIndexed { index, sample ->
                val frame = index / ManualAudioSource.FRAME_SAMPLES
                val expected = (100 * (frame + 1)) / 32768f
                assertEquals("sample $index out of order", expected, sample, 1e-6f)
            }
            assertEquals(1, created)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `idle unload after 300 s closes the engine and the next press loads a new one`() {
        val engines = mutableListOf<FakeSpeechEngine>()
        EngineFactory.factory = { _, _ ->
            FakeSpeechEngine(respond = { _, language -> Transcript("hello from fake", language) })
                .also { engines += it }
        }
        val controller = Robolectric.buildService(DictationService::class.java).create()
        val service = controller.get()
        try {
            invoke(service, "onPress")
            await("first engine is loaded") {
                engines.size == 1 && DictationService.state.value is DictationState.Recording
            }
            audio.emit(frames = 10)
            invoke(service, "onRelease")
            await("first transcript is inserted") { inserted.size == 1 }
            assertFalse(engines[0].closed)

            shadowOf(Looper.getMainLooper()).idleFor(301, TimeUnit.SECONDS)

            assertTrue("idle watchdog closes the engine", engines[0].closed)
            assertEquals(DictationState.Idle, DictationService.state.value)

            invoke(service, "onPress")
            await("a new engine is created for the next press") {
                engines.size == 2 && DictationService.state.value is DictationState.Recording
            }
            audio.emit(frames = 10, firstSample = 200)
            invoke(service, "onRelease")
            await("second transcript is inserted") { inserted.size == 2 }

            assertTrue(DictationService.state.value is DictationState.Success)
            assertEquals(2, engines.size)
            assertFalse(engines[1].closed)
            assertEquals(1, engines[1].received.size)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `cancel while the model loads inserts nothing and leaves the engine reusable`() {
        val loaderEntered = CountDownLatch(1)
        val loaderReturned = CountDownLatch(1)
        val releaseLoader = CountDownLatch(1)
        val engine = FakeSpeechEngine(
            respond = { _, language -> Transcript("hello from fake", language) },
        )
        var created = 0
        EngineFactory.factory = { _, _ ->
            if (created++ == 0) {
                loaderEntered.countDown()
                assertTrue(releaseLoader.await(5, TimeUnit.SECONDS))
                loaderReturned.countDown()
            }
            engine
        }
        val controller = Robolectric.buildService(DictationService::class.java).create()
        val service = controller.get()
        try {
            invoke(service, "onPress")
            assertTrue(loaderEntered.await(2, TimeUnit.SECONDS))
            audio.emit(frames = 5)

            invoke(service, "cancelSession")

            assertEquals(DictationState.Idle, DictationService.state.value)
            releaseLoader.countDown()
            assertTrue(loaderReturned.await(2, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue("a cancelled load must not insert", inserted.isEmpty())
            assertFalse("the loaded engine stays alive for the next session", engine.closed)

            invoke(service, "onPress")
            await("recording after the cancelled load") {
                DictationService.state.value is DictationState.Recording
            }
            audio.emit(frames = 10)
            invoke(service, "onRelease")
            await("the follow-up transcript is inserted") { inserted.size == 1 }

            assertEquals(1, created)
            assertEquals(1, engine.received.size)
            assertTrue(DictationService.state.value is DictationState.Success)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `switching the model between presses closes the previous engine`() {
        val engines = mutableListOf<FakeSpeechEngine>()
        EngineFactory.factory = { _, _ ->
            FakeSpeechEngine(respond = { _, language -> Transcript("hello from fake", language) })
                .also { engines += it }
        }
        val controller = Robolectric.buildService(DictationService::class.java).create()
        val service = controller.get()
        try {
            invoke(service, "onPress")
            await("base engine is loaded") {
                engines.size == 1 && DictationService.state.value is DictationState.Recording
            }
            audio.emit(frames = 10)
            invoke(service, "onRelease")
            await("base transcript is inserted") { inserted.size == 1 }
            assertFalse(engines[0].closed)

            settings.modelId = "tiny"

            invoke(service, "onPress")
            await("tiny engine is loaded") {
                engines.size == 2 && DictationService.state.value is DictationState.Recording
            }
            assertTrue("the previous model is closed on switch", engines[0].closed)
            assertFalse(engines[1].closed)

            audio.emit(frames = 10, firstSample = 200)
            invoke(service, "onRelease")
            await("tiny transcript is inserted") { inserted.size == 2 }

            assertTrue(DictationService.state.value is DictationState.Success)
            assertEquals(2, engines.size)
            assertEquals(1, engines[0].received.size)
            assertEquals(1, engines[1].received.size)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `start tap captures the focused target before any audio is captured`() {
        var captures = 0
        DictationService.targetCapture = { captures++ }
        val controller = Robolectric.buildService(DictationService::class.java).create()
        val service = controller.get()
        try {
            invoke(service, "onPress")
            await("recording starts") {
                DictationService.state.value is DictationState.Recording
            }
            assertEquals(1, captures)
            invoke(service, "cancelSession")
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `hallucinated transcript on real audio is rejected without insertion`() {
        val engine = FakeSpeechEngine(
            respond = { _, _ -> Transcript("Thank you.") },
        )
        EngineFactory.factory = { _, _ -> engine }
        val controller = Robolectric.buildService(DictationService::class.java).create()
        val service = controller.get()
        try {
            invoke(service, "onPress")
            await("recording starts") {
                DictationService.state.value is DictationState.Recording
            }
            audio.emit(frames = 10, firstSample = 1_000, step = 0)
            invoke(service, "onRelease")
            await("hallucination is rejected") {
                DictationService.state.value is DictationState.Error
            }

            val state = DictationService.state.value as DictationState.Error
            assertEquals(TranscriptGate.EMPTY_MESSAGE, state.message)
            assertTrue("nothing may be inserted", inserted.isEmpty())
            assertEquals(1, engine.received.size)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `digital silence is a microphone error and never reaches the engine or the field`() {
        val engine = FakeSpeechEngine(
            respond = { _, _ -> Transcript("Thank you.") },
        )
        EngineFactory.factory = { _, _ -> engine }
        val controller = Robolectric.buildService(DictationService::class.java).create()
        val service = controller.get()
        try {
            invoke(service, "onPress")
            await("recording starts") {
                DictationService.state.value is DictationState.Recording
            }
            audio.emit(frames = 10, firstSample = 0, step = 0)
            ShadowSystemClock.advanceBy(500, TimeUnit.MILLISECONDS)
            invoke(service, "onRelease")
            await("silent microphone is reported") {
                DictationService.state.value is DictationState.Error
            }

            val state = DictationService.state.value as DictationState.Error
            assertEquals(TranscriptGate.SILENT_MIC_MESSAGE, state.message)
            assertTrue("nothing may be inserted", inserted.isEmpty())
            assertTrue("all-zero audio must not reach the engine", engine.received.isEmpty())
        } finally {
            controller.destroy()
        }
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

    /** Audio source that stores the frame callback so the test emits frames on demand. */
    private class ManualAudioSource : AudioSource {
        private var onFrame: ((ShortArray, Long) -> Unit)? = null
        private var clockMs = 1_000L

        @Volatile
        var started = false
            private set

        @Synchronized
        override fun start(onFrame: (ShortArray, Long) -> Unit, onError: (Throwable) -> Unit) {
            this.onFrame = onFrame
            started = true
        }

        @Synchronized
        override fun stop() = Unit

        override fun release() = Unit

        fun emit(frames: Int, firstSample: Int = 100, step: Int = 100) {
            val callback = synchronized(this) { onFrame } ?: error("audio capture was not started")
            repeat(frames) { index ->
                val value = (firstSample + step * index).toShort()
                callback(ShortArray(FRAME_SAMPLES) { value }, clockMs)
                clockMs += 20L
            }
        }

        companion object {
            const val FRAME_SAMPLES = 320
        }
    }
}
