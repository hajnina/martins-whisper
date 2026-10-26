package com.martins.whisper.core.accel

import com.martins.whisper.core.ComputeBackend

enum class BackendKind { GPU, NPU, DSP, CPU }

data class BackendBenchmark(
    val backendId: String,
    val modelId: String,
    val loadMs: Long,
    val inferMs: Long,
    val audioMs: Long,
) {
    val rtf: Double
        get() = if (audioMs > 0) inferMs.toDouble() / audioMs else 0.0
}

data class BackendInfo(
    val id: String,
    val name: String,
    val kind: BackendKind,
    val vendor: String? = null,
    val available: Boolean,
    val reason: String? = null,
    val benchmark: BackendBenchmark? = null,
) {
    init { require(available || reason != null) { "unavailable BackendInfo must carry a reason" } }
}

interface AccelProbe {
    /**
     * One-shot capability scan. MUST always return at least a CPU entry
     * and MUST NOT throw.
     */
    fun probe(): List<BackendInfo>
}

object AccelSelection {
    /**
     * requested == CPU -> the CPU entry (reason from the CPU entry kept).
     * requested == GPU -> first available GPU entry (probe order = preference order), else CPU entry.
     * requested == AUTO -> first available non-CPU entry, else CPU entry.
     * Throws IllegalStateException if no CPU entry exists (probe contract violated).
     */
    fun select(requested: ComputeBackend, backends: List<BackendInfo>): BackendInfo {
        val cpu = backends.firstOrNull { it.kind == BackendKind.CPU }
            ?: throw IllegalStateException("no CPU entry in backends (probe contract violated)")
        return when (requested) {
            ComputeBackend.CPU -> cpu
            ComputeBackend.GPU -> backends.firstOrNull { it.kind == BackendKind.GPU && it.available } ?: cpu
            ComputeBackend.AUTO -> backends.firstOrNull { it.kind != BackendKind.CPU && it.available } ?: cpu
        }
    }
}
