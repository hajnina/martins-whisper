package com.martins.whisper.onboarding

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ModelStepPhase {
    data object Idle : ModelStepPhase
    data class Downloading(
        val fraction: Float?,
        val bytesDownloaded: Long,
        val totalBytes: Long?,
    ) : ModelStepPhase

    data object Installed : ModelStepPhase
    data class Failed(val message: String) : ModelStepPhase
}

class ModelStepState(
    private val install: suspend ((Float?, Long, Long?) -> Unit) -> Result<Unit>,
) {

    private val _phase = MutableStateFlow<ModelStepPhase>(ModelStepPhase.Idle)
    val phase: StateFlow<ModelStepPhase> = _phase.asStateFlow()

    private var running = false

    suspend fun start() {
        if (running || _phase.value is ModelStepPhase.Installed) return
        begin()
    }

    suspend fun retry() {
        if (running || _phase.value !is ModelStepPhase.Failed) return
        begin()
    }

    private suspend fun begin() {
        running = true
        try {
            val result = install { fraction, bytes, total ->
                _phase.value = ModelStepPhase.Downloading(fraction, bytes, total)
            }
            _phase.value = result.fold(
                onSuccess = { ModelStepPhase.Installed },
                onFailure = { ModelStepPhase.Failed(it.message ?: DOWNLOAD_FAILED) },
            )
        } finally {
            running = false
        }
    }

    private companion object {
        const val DOWNLOAD_FAILED = "Download failed"
    }
}
