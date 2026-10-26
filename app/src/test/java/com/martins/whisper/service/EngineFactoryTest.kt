package com.martins.whisper.service

import com.martins.whisper.asr.EnginePlanner
import com.martins.whisper.asr.SpeechEngineException
import com.martins.whisper.asr.SpeechEngineUnavailableException
import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.asr.EngineState
import com.martins.whisper.core.asr.FakeSpeechEngine
import com.martins.whisper.core.asr.ModelRepository
import com.martins.whisper.core.asr.ModelStorage
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EngineFactoryTest {

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
    fun `missing native library fails typed and records native-unavailable`() {
        var loaderCalled = false
        var state = EngineState()
        try {
            EngineFactory.createWith(
                modelId = "base",
                backend = ComputeBackend.AUTO,
                repository = repositoryWith("base"),
                nativeAvailable = { false },
                engineLoader = { loaderCalled = true; FakeSpeechEngine() },
                onState = { state = it },
            )
            fail("expected SpeechEngineUnavailableException")
        } catch (e: SpeechEngineUnavailableException) {
            assertEquals(EngineState.NATIVE_UNAVAILABLE, e.reason)
        }
        assertFalse(loaderCalled)
        assertNull(state.activeBackend)
        assertEquals(EngineState.NATIVE_UNAVAILABLE, state.fallbackReason)
        assertNotNull(state.lastError)
        assertTrue(state.lastError!!.contains("Native"))
    }

    @Test
    fun `missing model file fails typed and records model-missing`() {
        var loaderCalled = false
        var state = EngineState(gpuFailureCount = 2)
        try {
            EngineFactory.createWith(
                modelId = "base",
                backend = ComputeBackend.AUTO,
                repository = repositoryWith(),
                nativeAvailable = { true },
                engineLoader = { loaderCalled = true; FakeSpeechEngine() },
                previousState = state,
                onState = { state = it },
            )
            fail("expected SpeechEngineUnavailableException")
        } catch (e: SpeechEngineUnavailableException) {
            assertEquals(EngineState.MODEL_MISSING, e.reason)
            assertTrue(e.message!!.contains("not installed"))
        }
        assertFalse(loaderCalled)
        assertEquals(EngineState.MODEL_MISSING, state.fallbackReason)
        assertNotNull(state.lastError)
        assertTrue(state.lastError!!.contains("not installed"))
        assertEquals(2, state.gpuFailureCount)
    }

    @Test
    fun `unknown persisted id falls back to the default catalog model`() {
        val repository = repositoryWith("base")
        val plan = EnginePlanner.plan("stale-id", ComputeBackend.AUTO, repository)
        assertEquals("base", plan.spec.id)
    }

    @Test
    fun `auto backend falling back to cpu is persisted with a reason`() {
        var state = EngineState(gpuFailureCount = 1)
        val fake = FakeSpeechEngine(backend = "whisper.cpp/cpu")
        val loaded = EngineFactory.createWith(
            modelId = "base",
            backend = ComputeBackend.AUTO,
            repository = repositoryWith("base"),
            nativeAvailable = { true },
            engineLoader = { fake },
            previousState = state,
            onState = { state = it },
        )
        assertSame(fake, loaded)
        assertEquals("whisper.cpp/cpu", state.activeBackend)
        assertEquals(EngineState.AUTO_CPU, state.fallbackReason)
        assertEquals(2, state.gpuFailureCount)
        assertNull(state.lastError)
    }

    @Test
    fun `explicit cpu request is not counted as a gpu fallback`() {
        var state = EngineState(gpuFailureCount = 4)
        EngineFactory.createWith(
            modelId = "base",
            backend = ComputeBackend.CPU,
            repository = repositoryWith("base"),
            nativeAvailable = { true },
            engineLoader = { FakeSpeechEngine(backend = "whisper.cpp/cpu") },
            previousState = state,
            onState = { state = it },
        )
        assertNull(state.fallbackReason)
        assertEquals(4, state.gpuFailureCount)
    }

    @Test
    fun `vulkan backend keeps no fallback reason`() {
        var state = EngineState()
        EngineFactory.createWith(
            modelId = "base",
            backend = ComputeBackend.GPU,
            repository = repositoryWith("base"),
            nativeAvailable = { true },
            engineLoader = { FakeSpeechEngine(backend = "whisper.cpp/vulkan") },
            onState = { state = it },
        )
        assertNull(state.fallbackReason)
        assertEquals(0, state.gpuFailureCount)
        assertEquals("whisper.cpp/vulkan", state.activeBackend)
    }

    @Test
    fun `requested gpu that ran on cpu records gpu-unavailable`() {
        var state = EngineState()
        EngineFactory.createWith(
            modelId = "base",
            backend = ComputeBackend.GPU,
            repository = repositoryWith("base"),
            nativeAvailable = { true },
            engineLoader = { FakeSpeechEngine(backend = "whisper.cpp/cpu") },
            onState = { state = it },
        )
        assertEquals(EngineState.GPU_UNAVAILABLE, state.fallbackReason)
        assertEquals(1, state.gpuFailureCount)
    }

    @Test
    fun `engine loader exceptions are recorded and rethrown typed`() {
        var state = EngineState()
        try {
            EngineFactory.createWith(
                modelId = "base",
                backend = ComputeBackend.AUTO,
                repository = repositoryWith("base"),
                nativeAvailable = { true },
                engineLoader = { throw SpeechEngineException("native init failed", code = -3) },
                onState = { state = it },
            )
            fail("expected SpeechEngineException")
        } catch (e: SpeechEngineException) {
            assertEquals(-3, e.code)
        }
        assertEquals(EngineState.ENGINE_ERROR, state.fallbackReason)
        assertEquals("native init failed", state.lastError)
    }
}
