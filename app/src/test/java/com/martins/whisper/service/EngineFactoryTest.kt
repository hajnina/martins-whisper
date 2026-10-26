package com.martins.whisper.service

import com.martins.whisper.asr.EngineDiagnostics
import com.martins.whisper.asr.SpeechEngineException
import com.martins.whisper.asr.SpeechEngineUnavailableException
import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.asr.FakeSpeechEngine
import com.martins.whisper.core.asr.ModelRepository
import com.martins.whisper.core.asr.ModelStorage
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EngineFactoryTest {

    private class Diagnostics : EngineDiagnostics {
        override var lastModelId: String? = null
        override var lastBackend: String? = null
        override var lastFallbackReason: String? = null
        override var gpuFailureCount: Int = 0
        override var lastEngineError: String? = null
    }

    private fun repositoryWith(vararg installed: String): ModelRepository {
        val root = Files.createTempDirectory("engine-factory-test").toFile()
        root.deleteOnExit()
        val repository = ModelRepository(ModelStorage(root))
        repository.storage.ensureModelsDir()
        installed.forEach { id ->
            File(repository.modelsDir, "$id.bin").writeBytes(ByteArray(8) { 1 })
        }
        return repository
    }

    @Test
    fun `missing native library fails typed and records the error`() {
        val diagnostics = Diagnostics()
        var loaderCalled = false
        try {
            EngineFactory.createWith(
                modelId = "base",
                backend = ComputeBackend.AUTO,
                repository = repositoryWith("base"),
                nativeAvailable = { false },
                engineLoader = { loaderCalled = true; FakeSpeechEngine() },
                diagnostics = diagnostics,
            )
            fail("expected SpeechEngineUnavailableException")
        } catch (_: SpeechEngineUnavailableException) {
        }
        assertEquals(false, loaderCalled)
        assertNull(diagnostics.lastBackend)
        assertNotNull(diagnostics.lastEngineError)
        assertTrue(diagnostics.lastEngineError!!.contains("Native"))
    }

    @Test
    fun `missing model file fails typed and records the error`() {
        val diagnostics = Diagnostics()
        var loaderCalled = false
        try {
            EngineFactory.createWith(
                modelId = "base",
                backend = ComputeBackend.AUTO,
                repository = repositoryWith(),
                nativeAvailable = { true },
                engineLoader = { loaderCalled = true; FakeSpeechEngine() },
                diagnostics = diagnostics,
            )
            fail("expected SpeechEngineUnavailableException")
        } catch (e: SpeechEngineUnavailableException) {
            assertTrue(e.message!!.contains("not installed"))
        }
        assertEquals(false, loaderCalled)
        assertNotNull(diagnostics.lastEngineError)
        assertTrue(diagnostics.lastEngineError!!.contains("not installed"))
    }

    @Test
    fun `auto backend falling back to cpu is persisted with a reason`() {
        val diagnostics = Diagnostics()
        val fake = FakeSpeechEngine(backend = "whisper.cpp/cpu")
        val loaded = EngineFactory.createWith(
            modelId = "base",
            backend = ComputeBackend.AUTO,
            repository = repositoryWith("base"),
            nativeAvailable = { true },
            engineLoader = { fake },
            diagnostics = diagnostics,
        )
        assertSame(fake, loaded)
        assertEquals("base", diagnostics.lastModelId)
        assertEquals("whisper.cpp/cpu", diagnostics.lastBackend)
        assertNotNull(diagnostics.lastFallbackReason)
        assertTrue(diagnostics.lastFallbackReason!!.contains("CPU"))
        assertEquals(1, diagnostics.gpuFailureCount)
        assertNull(diagnostics.lastEngineError)
    }

    @Test
    fun `explicit cpu request is not counted as a gpu fallback`() {
        val diagnostics = Diagnostics()
        EngineFactory.createWith(
            modelId = "base",
            backend = ComputeBackend.CPU,
            repository = repositoryWith("base"),
            nativeAvailable = { true },
            engineLoader = { FakeSpeechEngine(backend = "whisper.cpp/cpu") },
            diagnostics = diagnostics,
        )
        assertNull(diagnostics.lastFallbackReason)
        assertEquals(0, diagnostics.gpuFailureCount)
    }

    @Test
    fun `vulkan backend keeps no fallback reason`() {
        val diagnostics = Diagnostics()
        EngineFactory.createWith(
            modelId = "base",
            backend = ComputeBackend.GPU,
            repository = repositoryWith("base"),
            nativeAvailable = { true },
            engineLoader = { FakeSpeechEngine(backend = "whisper.cpp/vulkan") },
            diagnostics = diagnostics,
        )
        assertNull(diagnostics.lastFallbackReason)
        assertEquals(0, diagnostics.gpuFailureCount)
        assertEquals("whisper.cpp/vulkan", diagnostics.lastBackend)
    }

    @Test
    fun `engine loader exceptions are recorded and rethrown typed`() {
        val diagnostics = Diagnostics()
        try {
            EngineFactory.createWith(
                modelId = "base",
                backend = ComputeBackend.AUTO,
                repository = repositoryWith("base"),
                nativeAvailable = { true },
                engineLoader = { throw SpeechEngineException("native init failed", code = -3) },
                diagnostics = diagnostics,
            )
            fail("expected SpeechEngineException")
        } catch (e: SpeechEngineException) {
            assertEquals(-3, e.code)
        }
        assertEquals("native init failed", diagnostics.lastEngineError)
    }

    @Test
    fun `unknown persisted id loads a user imported model by file id`() {
        val diagnostics = Diagnostics()
        val fake = FakeSpeechEngine(backend = "whisper.cpp/vulkan")
        EngineFactory.createWith(
            modelId = "my-custom",
            backend = ComputeBackend.AUTO,
            repository = repositoryWith("my-custom"),
            nativeAvailable = { true },
            engineLoader = { fake },
            diagnostics = diagnostics,
        )
        assertEquals("my-custom", diagnostics.lastModelId)
        assertEquals("whisper.cpp/vulkan", diagnostics.lastBackend)
    }
}
