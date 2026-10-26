package com.martins.whisper.core.accel

import com.martins.whisper.core.ComputeBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AccelProbeTest {
    private val cpuAvailable = BackendInfo(
        id = "cpu",
        name = "CPU (XNNPACK)",
        kind = BackendKind.CPU,
        available = true,
    )
    private val cpuUnavailable = BackendInfo(
        id = "cpu",
        name = "CPU (XNNPACK)",
        kind = BackendKind.CPU,
        available = false,
        reason = "no crash-free XNNPACK load",
    )
    private val gpuOpenclAdreno = BackendInfo(
        id = "opencl-adreno",
        name = "OpenCL Adreno GPU",
        kind = BackendKind.GPU,
        vendor = "opencl-adreno",
        available = true,
    )
    private val gpuUnavailable = BackendInfo(
        id = "opencl-adreno",
        name = "OpenCL Adreno GPU",
        kind = BackendKind.GPU,
        vendor = "opencl-adreno",
        available = false,
        reason = "OpenCL init returned CL_PLATFORM_NOT_FOUND_KHR",
    )

    private fun benchmark(audioMs: Long = 10_000, inferMs: Long = 4_000) = BackendBenchmark(
        backendId = "opencl-adreno",
        modelId = "whisper-tiny",
        loadMs = 1_200,
        inferMs = inferMs,
        audioMs = audioMs,
    )

    @Test
    fun `probe of FakeAccelProbe returns the backends it was constructed with`() {
        val probe = FakeAccelProbe(listOf(gpuOpenclAdreno, cpuAvailable))
        val result = probe.probe()
        assertEquals(listOf(gpuOpenclAdreno, cpuAvailable), result)
        assertTrue(result.any { it.kind == BackendKind.CPU })
    }

    @Test
    fun `unavailable BackendInfo without reason throws`() {
        val e = assertFailsWith<IllegalArgumentException> {
            BackendInfo(
                id = "npu-qnn",
                name = "QNN NPU",
                kind = BackendKind.NPU,
                available = false,
            )
        }
        assertTrue(e.message!!.contains("unavailable BackendInfo must carry a reason"))
    }

    @Test
    fun `available BackendInfo without reason is fine and unavailable with reason keeps it`() {
        assertNull(gpuOpenclAdreno.reason)
        assertEquals("no crash-free XNNPACK load", cpuUnavailable.reason)
    }

    @Test
    fun `rtf is inferMs divided by audioMs`() {
        assertEquals(0.4, benchmark().rtf, 1e-9)
    }

    @Test
    fun `rtf is zero when audioMs is zero`() {
        assertEquals(0.0, benchmark(audioMs = 0).rtf)
    }

    @Test
    fun `select CPU returns the CPU entry with its reason kept`() {
        val selected = AccelSelection.select(ComputeBackend.CPU, listOf(gpuOpenclAdreno, cpuUnavailable))
        assertEquals(cpuUnavailable, selected)
        assertEquals("no crash-free XNNPACK load", selected.reason)
    }

    @Test
    fun `select GPU returns first available GPU entry in probe order`() {
        val secondGpu = gpuOpenclAdreno.copy(id = "vulkan-mali", name = "Vulkan Mali GPU")
        val selected = AccelSelection.select(
            ComputeBackend.GPU,
            listOf(gpuOpenclAdreno, secondGpu, cpuAvailable),
        )
        assertEquals(gpuOpenclAdreno, selected)
    }

    @Test
    fun `select GPU falls back to CPU when no GPU is available`() {
        val selected = AccelSelection.select(
            ComputeBackend.GPU,
            listOf(gpuUnavailable, cpuAvailable),
        )
        assertEquals(cpuAvailable, selected)
    }

    @Test
    fun `select GPU with only CPU backends falls back to CPU entry`() {
        val selected = AccelSelection.select(ComputeBackend.GPU, listOf(cpuAvailable))
        assertEquals(cpuAvailable, selected)
    }

    @Test
    fun `select AUTO returns first available non-CPU entry whether GPU NPU or DSP`() {
        val dsp = BackendInfo(id = "dsp-hexagon", name = "Hexagon DSP", kind = BackendKind.DSP, available = true)
        assertEquals(
            gpuOpenclAdreno,
            AccelSelection.select(ComputeBackend.AUTO, listOf(gpuOpenclAdreno, dsp, cpuAvailable)),
        )
        assertEquals(
            dsp,
            AccelSelection.select(ComputeBackend.AUTO, listOf(gpuUnavailable, dsp, cpuAvailable)),
        )
    }

    @Test
    fun `select AUTO skips unavailable non-CPU entries`() {
        val selected = AccelSelection.select(
            ComputeBackend.AUTO,
            listOf(gpuUnavailable, cpuAvailable),
        )
        assertEquals(cpuAvailable, selected)
    }

    @Test
    fun `select AUTO with zero non-CPU backends returns the CPU entry`() {
        val selected = AccelSelection.select(ComputeBackend.AUTO, listOf(cpuAvailable))
        assertEquals(cpuAvailable, selected)
    }

    @Test
    fun `select throws IllegalStateException when no CPU entry exists`() {
        assertFailsWith<IllegalStateException> {
            AccelSelection.select(ComputeBackend.AUTO, listOf(gpuOpenclAdreno))
        }
        assertFailsWith<IllegalStateException> {
            AccelSelection.select(ComputeBackend.CPU, emptyList())
        }
    }

    @Test
    fun `benchmark is attached to BackendInfo and exposed`() {
        val info = gpuOpenclAdreno.copy(benchmark = benchmark())
        assertEquals("whisper-tiny", info.benchmark?.modelId)
        assertNotNull(info.benchmark)
    }
}
