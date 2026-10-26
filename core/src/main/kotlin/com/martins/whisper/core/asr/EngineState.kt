package com.martins.whisper.core.asr

import com.martins.whisper.core.ComputeBackend

/**
 * Last resolved engine status. Persisted by the app so the diagnostics screen can explain why
 * dictation is unavailable or why it is running on the CPU.
 *
 * [fallbackReason] is one of the constants below on failure or fallback, and null after a clean
 * load on the requested backend. [lastError] is the human-readable message shown to the user.
 */
data class EngineState(
    val activeBackend: String? = null,
    val fallbackReason: String? = null,
    val gpuFailureCount: Int = 0,
    val lastError: String? = null,
) {
    val isLoaded: Boolean
        get() = activeBackend != null

    /** Records a successful load and derives whether the requested GPU path was actually used. */
    fun loaded(backend: String, requested: ComputeBackend): EngineState {
        val gpuUsed = backend.contains("gpu", ignoreCase = true) ||
            backend.contains("vulkan", ignoreCase = true)
        val fellBack = !gpuUsed && requested != ComputeBackend.CPU
        return EngineState(
            activeBackend = backend,
            fallbackReason = when {
                !fellBack -> null
                requested == ComputeBackend.GPU -> GPU_UNAVAILABLE
                else -> AUTO_CPU
            },
            gpuFailureCount = gpuFailureCount + if (fellBack) 1 else 0,
            lastError = null,
        )
    }

    /** Records a typed failure; [message] falls back to [reason] when the cause had no message. */
    fun failed(reason: String, message: String?): EngineState = EngineState(
        activeBackend = null,
        fallbackReason = reason,
        gpuFailureCount = gpuFailureCount,
        lastError = message?.takeIf { it.isNotBlank() } ?: reason,
    )

    companion object {
        const val NATIVE_UNAVAILABLE: String = "native-unavailable"
        const val MODEL_MISSING: String = "model-missing"
        const val GPU_UNAVAILABLE: String = "gpu-unavailable"
        const val AUTO_CPU: String = "auto-cpu"
        const val ENGINE_ERROR: String = "engine-error"

        val EMPTY: EngineState = EngineState()
    }
}
