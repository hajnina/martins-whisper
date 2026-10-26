package com.martins.whisper.asr

import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.ModelSpec
import com.martins.whisper.core.asr.EngineState
import com.martins.whisper.core.asr.ModelCatalog
import com.martins.whisper.core.asr.ModelRepository
import java.io.File

/** Everything the engine factory needs to load one model; produced by [EnginePlanner]. */
data class EnginePlan(
    val spec: ModelSpec,
    val modelFile: File,
    val backend: ComputeBackend,
)

/**
 * Pure model-selection logic (no Android, no JNI) so it can be tested on the host JVM.
 *
 * A stale or unknown persisted id falls back to the catalog default instead of crashing on
 * startup. The file exists check is the same one the engine uses, so a plan either points at a
 * readable model or fails with an actionable [SpeechEngineUnavailableException].
 */
object EnginePlanner {

    fun plan(
        modelId: String,
        backend: ComputeBackend,
        repository: ModelRepository,
        customModels: List<ModelSpec> = emptyList(),
    ): EnginePlan {
        val spec = customModels.firstOrNull { it.id == modelId }
            ?: ModelCatalog.byId(modelId)
            ?: ModelCatalog.default
        val file = repository.modelFile(spec)
        if (!repository.isInstalled(spec)) {
            throw SpeechEngineUnavailableException(
                "Model '${spec.displayName}' is not installed. Download or import it in Settings.",
                EngineState.MODEL_MISSING,
            )
        }
        return EnginePlan(spec = spec, modelFile = file, backend = backend)
    }
}
