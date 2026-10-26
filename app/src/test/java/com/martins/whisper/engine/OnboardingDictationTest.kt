package com.martins.whisper.engine

import com.martins.whisper.audio.AudioSource
import com.martins.whisper.core.AudioChunk
import com.martins.whisper.core.ModelSpec
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.Transcript
import com.martins.whisper.core.asr.ModelCatalog
import com.martins.whisper.core.asr.ModelRepository
import com.martins.whisper.core.asr.ModelStorage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OnboardingDictationTest {

    private lateinit var storageRoot: File
    private lateinit var repository: ModelRepository
    private val spec: ModelSpec = ModelCatalog.default

    @Before
    fun setUp() {
        storageRoot = createTempDir(prefix = "onboarding-dictation")
        repository = ModelRepository(ModelStorage(storageRoot))
    }

    @After
    fun tearDown() {
        storageRoot.deleteRecursively()
    }

    private fun installModelFile() {
        repository.modelFile(spec).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
    }

    private fun dictation(
        source: AudioSource = FakeAudioSource(),
        engine: SpeechEngine = FakeEngine(),
        runtimePresent: Boolean = true,
    ) = OnboardingDictation(spec, repository, source, { engine }, { runtimePresent })

    @Test
    fun `model step reads the same repository the engine uses`() {
        val dictation = dictation()
        assertFalse(dictation.modelInstalled())
        assertEquals(com.martins.whisper.core.engine.EngineAvailability.MODEL_MISSING, dictation.status().availability)

        installModelFile()
        assertTrue(dictation.modelInstalled())
        assertEquals(com.martins.whisper.core.engine.EngineAvailability.AVAILABLE, dictation.status().availability)
    }

    @Test
    fun `missing native runtime is reported instead of being opened`() {
        installModelFile()
        val dictation = dictation(runtimePresent = false)
        assertEquals(com.martins.whisper.core.engine.EngineAvailability.RUNTIME_MISSING, dictation.status().availability)
        assertFalse(dictation.start())
    }

    @Test
    fun `stop transcribes the captured buffer once with timings`() = runBlocking {
        installModelFile()
        val source = FakeAudioSource()
        val engine = FakeEngine(text = "hello world")
        val dictation = dictation(source = source, engine = engine)

        assertTrue(dictation.start())
        source.emit(ShortArray(1_600) { 1_000 })

        val result = dictation.stop("cs")

        assertEquals("hello world", result.text)
        assertEquals("whisper", result.engineId)
        assertEquals(100L, result.audioMs)
        assertTrue(result.ok)
        assertEquals("cs", engine.language)
        assertEquals(1, engine.calls)
        assertTrue(engine.closed)
        assertEquals(0f, dictation.level)
    }

    @Test
    fun `empty capture is an error result and does not open the engine`() = runBlocking {
        installModelFile()
        val engine = FakeEngine()
        val dictation = dictation(engine = engine)

        assertTrue(dictation.start())
        val result = dictation.stop()

        assertEquals("", result.text)
        assertFalse(result.ok)
        assertEquals("no audio captured", result.error)
        assertEquals(0, engine.calls)
    }

    @Test
    fun `engine failure becomes an error result and the engine is closed`() = runBlocking {
        installModelFile()
        val source = FakeAudioSource()
        val engine = FakeEngine(failWith = IllegalStateException("boom"))
        val dictation = dictation(source = source, engine = engine)

        assertTrue(dictation.start())
        source.emit(ShortArray(800) { 500 })
        val result = dictation.stop()

        assertEquals("", result.text)
        assertEquals("boom", result.error)
        assertEquals(50L, result.audioMs)
        assertTrue(engine.closed)
    }

    @Test
    fun `cancel drops the buffer`() = runBlocking {
        installModelFile()
        val source = FakeAudioSource()
        val engine = FakeEngine()
        val dictation = dictation(source = source, engine = engine)

        assertTrue(dictation.start())
        source.emit(ShortArray(800) { 500 })
        dictation.cancel()

        val result = dictation.stop()
        assertFalse(result.ok)
        assertEquals(0, engine.calls)
        assertTrue(source.stopped)
    }

    private class FakeAudioSource : AudioSource {
        private var onFrame: ((ShortArray, Long) -> Unit)? = null
        var stopped = false
            private set

        override fun start(onFrame: (ShortArray, Long) -> Unit, onError: (Throwable) -> Unit) {
            this.onFrame = onFrame
            stopped = false
        }

        fun emit(frame: ShortArray) {
            onFrame?.invoke(frame, 0L)
        }

        override fun stop() {
            stopped = true
        }

        override fun release() = Unit
    }

    private class FakeEngine(
        private val text: String = "text",
        private val failWith: Throwable? = null,
    ) : SpeechEngine {
        override val backend: String = "fake"
        var calls = 0
            private set
        var language: String? = null
            private set
        var closed = false
            private set

        override suspend fun transcribe(chunk: AudioChunk, language: String?): Transcript {
            calls++
            this.language = language
            failWith?.let { throw it }
            return Transcript(text = text, language = language)
        }

        override fun close() {
            closed = true
        }
    }
}
