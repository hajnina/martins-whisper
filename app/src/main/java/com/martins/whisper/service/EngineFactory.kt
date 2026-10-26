package com.martins.whisper.service

import android.content.Context
import com.martins.whisper.asr.EnginePlan
import com.martins.whisper.asr.EnginePlanner
import com.martins.whisper.asr.ModelRepositoryProvider
import com.martins.whisper.asr.SpeechEngineUnavailableException
import com.martins.whisper.asr.WhisperCppEngine
import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.asr.EngineState
import com.martins.whisper.core.asr.ModelRepository
import com.martins.whisper.data.SettingsStore

/**
 * Builds the real [WhisperCppEngine] from the installed catalog model. The [factory] hook exists
 * for tests only; the default path never returns a fake engine and records a typed [EngineState]
 * for every success, fallback and failure.
 */
object EngineFactory {

    @Volatile
    var factory: (Context, SettingsStore) -> SpeechEngine = { context, settings ->
        createDefault(context, settings)
    }

    fun create(context: Context, settings: SettingsStore): SpeechEngine =
        factory(context.applicationContext, settings)

    private fun createDefault(context: Context, settings: SettingsStore): SpeechEngine =
        createWith(
            modelId = settings.modelId,
            backend = settings.backend,
            repository = ModelRepositoryProvider.forContext(context),
            nativeAvailable = { WhisperCppEngine.isNativeAvailable },
            engineLoader = { plan -> WhisperCppEngine.create(plan.modelFile.path, plan.backend) },
            previousState = settings.engineState,
            onState = { settings.engineState = it },
        )

    fun createWith(
        modelId: String,
        backend: ComputeBackend,
        repository: ModelRepository,
        nativeAvailable: () -> Boolean,
        engineLoader: (EnginePlan) -> SpeechEngine,
        previousState: EngineState = EngineState.EMPTY,
        onState: (EngineState) -> Unit = {},
    ): SpeechEngine {
        val plan = try {
            EnginePlanner.plan(modelId, backend, repository)
        } catch (e: SpeechEngineUnavailableException) {
            onState(previousState.failed(e.reason, e.message))
            throw e
        }

        if (!nativeAvailable()) {
            val detail = WhisperCppEngine.nativeLoadError
            val message = buildString {
                append("Native whisper.cpp is not available in this APK. Install a build with native support")
                if (!detail.isNullOrBlank()) append(" (").append(detail).append(')')
            }
            val error = SpeechEngineUnavailableException(message, EngineState.NATIVE_UNAVAILABLE)
            onState(previousState.failed(error.reason, error.message))
            throw error
        }

        val engine = try {
            engineLoader(plan)
        } catch (e: SpeechEngineUnavailableException) {
            onState(previousState.failed(e.reason, e.message))
            throw e
        } catch (t: Throwable) {
            onState(previousState.failed(EngineState.ENGINE_ERROR, t.message))
            throw t
        }

        onState(previousState.loaded(engine.backend, plan.backend))
        return engine
    }
}
