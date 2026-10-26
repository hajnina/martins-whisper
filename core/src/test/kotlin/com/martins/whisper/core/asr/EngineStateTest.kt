package com.martins.whisper.core.asr

import com.martins.whisper.core.ComputeBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineStateTest {

    @Test
    fun `clean gpu load keeps no fallback`() {
        val state = EngineState().loaded("whisper.cpp/vulkan", ComputeBackend.AUTO)
        assertEquals("whisper.cpp/vulkan", state.activeBackend)
        assertNull(state.fallbackReason)
        assertEquals(0, state.gpuFailureCount)
        assertNull(state.lastError)
        assertTrue(state.isLoaded)
    }

    @Test
    fun `explicit cpu load is not a gpu fallback`() {
        val state = EngineState(gpuFailureCount = 3).loaded("whisper.cpp/cpu", ComputeBackend.CPU)
        assertNull(state.fallbackReason)
        assertEquals(3, state.gpuFailureCount)
    }

    @Test
    fun `auto cpu load records auto-cpu and increments the counter`() {
        val state = EngineState(gpuFailureCount = 2).loaded("whisper.cpp/cpu", ComputeBackend.AUTO)
        assertEquals(EngineState.AUTO_CPU, state.fallbackReason)
        assertEquals(3, state.gpuFailureCount)
    }

    @Test
    fun `requested gpu on cpu records gpu-unavailable`() {
        val state = EngineState().loaded("whisper.cpp/cpu", ComputeBackend.GPU)
        assertEquals(EngineState.GPU_UNAVAILABLE, state.fallbackReason)
        assertEquals(1, state.gpuFailureCount)
    }

    @Test
    fun `failure clears the backend and keeps the gpu counter`() {
        val state = EngineState(activeBackend = "whisper.cpp/vulkan", gpuFailureCount = 5)
            .failed(EngineState.MODEL_MISSING, "Model 'Base' is not installed")
        assertNull(state.activeBackend)
        assertFalse(state.isLoaded)
        assertEquals(EngineState.MODEL_MISSING, state.fallbackReason)
        assertEquals(5, state.gpuFailureCount)
        assertEquals("Model 'Base' is not installed", state.lastError)
    }

    @Test
    fun `failure without a message uses the reason code`() {
        val state = EngineState().failed(EngineState.NATIVE_UNAVAILABLE, null)
        assertEquals(EngineState.NATIVE_UNAVAILABLE, state.fallbackReason)
        assertEquals(EngineState.NATIVE_UNAVAILABLE, state.lastError)
    }

    @Test
    fun `success clears a previous error`() {
        val state = EngineState(lastError = "boom", fallbackReason = EngineState.ENGINE_ERROR)
            .loaded("whisper.cpp/cpu", ComputeBackend.CPU)
        assertNull(state.lastError)
        assertNull(state.fallbackReason)
    }
}
