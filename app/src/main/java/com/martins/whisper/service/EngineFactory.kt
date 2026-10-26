package com.martins.whisper.service

import android.content.Context
import com.martins.whisper.asr.EngineDiagnostics
import com.martins.whisper.asr.EnginePlan
import com.martins.whisper.asr.EnginePlanner
import com.martins.whisper.asr.ModelRepositoryProvider
import com.martins.whisper.asr.SpeechEngineException
import com.martins.whisper.asr.SpeechEngineUnavailableException
import com.martins.whisper.asr.WhisperCppEngine
import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.asr.ModelRepository
import com.martins.whisper.data.SettingsStore

/**
 * Single seam for building the speech engine. The default path loads the selected catalog model
 * through [WhisperCppEngine]; tests may replace [factory] with a deterministically fake engine.
 *
 * A missing native library or missing model file never crashes the app: the typed
 * [SpeechEngineUnavailableException] is recorded in [EngineDiagnostics] and shown to the user.
 */
object EngineFactory {

    @Volatile
    var factory: (Context, SettingsStore) -> SpeechEngine = { context, settings ->
        createDefault(context, settings)
    }

    fun create(context: Context, settings: SettingsStore): SpeechEngine =
        factory(context.applicationContext ?: context, settings)

    /** Production path: real whisper.cpp engine with diagnostics persisted in [settings]. */
    fun createDefault(context: Context, settings: SettingsStore): SpeechEngine = createWith(
        modelId = settings.modelId,
        backend = settings.backend,
        repository = ModelRepositoryProvider.forContext(context),
        nativeAvailable = { WhisperCppEngine.isNativeAvailable },
        engineLoader = { plan -> WhisperCppEngine.create(plan.modelFile.path, plan.backend) },
        diagnostics = settings,
    )

    internal fun createWith(
        modelId: String,
        backend: ComputeBackend,
        repository: ModelRepository,
        nativeAvailable: () -> Boolean,
        engineLoader: (EnginePlan) -> SpeechEngine,
        diagnostics: EngineDiagnostics,
    ): SpeechEngine {
        try {
            if (!nativeAvailable()) {
                val detail = WhisperCppEngine.nativeLoadError?.let { ": $it" }.orEmpty()
                throw SpeechEngineUnavailableException(
                    "Native whisper.cpp is unavailable$detail. Install the native build (arm64-v8a).",
                )
            }
            val plan = EnginePlanner.plan(modelId, backend, repository)
            val engine = engineLoader(plan)
            val fallback = fallbackReason(plan.backend, engine.backend)
            diagnostics.lastModelId = plan.spec.id
            diagnostics.lastBackend = engine.backend
            diagnostics.lastFallbackReason = fallback
            if (fallback != null) diagnostics.gpuFailureCount = diagnostics.gpuFailureCount + 1
            diagnostics.lastEngineError = null
            return engine
        } catch (t: Throwable) {
            diagnostics.lastEngineError = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            throw when (t) {
                is SpeechEngineUnavailableException, is SpeechEngineException -> t
                else -> SpeechEngineUnavailableException(
                    message = "Voice engine failed to start: ${t.message ?: t.javaClass.simpleName}",
                    cause = t,
                )
            }
        }
    }

    internal fun fallbackReason(requested: ComputeBackend, actualBackend: String): String? {
        if (requested == ComputeBackend.CPU) return null
        if (!actualBackend.endsWith("/cpu", ignoreCase = true)) return null
        return "Requested ${requested.name} but whisper.cpp runs on CPU: " +
            "GPU backend unavailable or GPU initialization failed."
    }
}
