package com.martins.whisper.asr

import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.ModelSpec
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
 * Unknown persisted ids are treated as user-imported models: their file is `<models>/<id>.bin`,
 * which is the same layout [ModelRepository] uses.
 */
object EnginePlanner {

    fun plan(modelId: String, backend: ComputeBackend, repository: ModelRepository): EnginePlan {
        val spec = ModelCatalog.byId(modelId) ?: customSpec(modelId)
        val file = repository.modelFile(spec)
        if (!repository.isInstalled(spec)) {
            throw SpeechEngineUnavailableException(
                "Model '${spec.displayName}' is not installed. Download or import it in Settings.",
            )
        }
        return EnginePlan(spec = spec, modelFile = file, backend = backend)
    }

    fun customSpec(modelId: String): ModelSpec {
        require(modelId.isNotBlank()) { "model id must not be blank" }
        return ModelCatalog.custom(
            id = modelId,
            fileName = "$modelId.bin",
            displayName = modelId,
        )
    }
}
