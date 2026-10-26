package com.martins.whisper.engine

import android.content.Context
import com.martins.whisper.asr.ModelRepositoryProvider
import com.martins.whisper.asr.WhisperCppEngine
import com.martins.whisper.audio.AudioRecordSource
import com.martins.whisper.audio.AudioSource
import com.martins.whisper.core.ModelSpec
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.asr.DownloadProgress
import com.martins.whisper.core.asr.DownloadResult
import com.martins.whisper.core.asr.ModelCatalog
import com.martins.whisper.core.asr.ModelRepository
import com.martins.whisper.core.engine.CompareRunner
import com.martins.whisper.core.engine.EngineIds
import com.martins.whisper.core.engine.EngineProbe
import com.martins.whisper.core.engine.EngineRegistration
import com.martins.whisper.core.engine.EngineRegistry
import com.martins.whisper.core.engine.EngineResult
import com.martins.whisper.core.engine.EngineStatus
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.service.EngineFactory
import kotlin.math.sqrt

/**
 * Whisper-only bridge for the onboarding model + first-dictation steps. There is no engine
 * choice in B3: the registry contains the existing whisper engine, the model comes from the same
 * [ModelRepository] the service uses, and one tap-to-tap buffer produces one [EngineResult].
 *
 * The Android glue is [create]; the constructor takes plain collaborators so the capture ->
 * transcribe path is unit-testable without a device (fake [AudioSource], fake [SpeechEngine]).
 */
class OnboardingDictation(
    private val spec: ModelSpec,
    private val repository: ModelRepository,
    private val audioSource: AudioSource,
    private val engineFactory: () -> SpeechEngine,
    private val runtimePresent: () -> Boolean = { true },
) {

    private val registry = EngineRegistry(
        registrations = listOf(
            EngineRegistration(
                id = EngineIds.WHISPER,
                displayName = "Whisper",
                probe = {
                    EngineProbe(
                        modelInstalled = repository.isInstalled(spec),
                        runtimePresent = runtimePresent(),
                    )
                },
                factory = engineFactory,
            ),
        ),
    )

    private val lock = Any()
    private val frames = ArrayList<ShortArray>()

    @Volatile
    private var listening = false

    @Volatile
    private var captureError: String? = null

    /** Latest frame loudness, 0..1, for the practice meter. */
    @Volatile
    var level: Float = 0f
        private set

    fun modelSpec(): ModelSpec = spec

    fun modelInstalled(): Boolean = repository.isInstalled(spec)

    fun status(): EngineStatus = registry.status(EngineIds.WHISPER)
        ?: EngineStatus.of(EngineIds.WHISPER, "Whisper", modelInstalled = modelInstalled(), runtimePresent = false)

    /** Same repository and hash check as the settings screen, so a download here is engine-visible. */
    fun installModel(
        force: Boolean = false,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadResult = repository.install(spec, force, onProgress)

    /** Starts capture; false when the model/runtime is not ready (UI keeps the button disabled). */
    fun start(): Boolean {
        if (listening) return true
        if (!status().available) return false
        synchronized(lock) { frames.clear() }
        captureError = null
        level = 0f
        listening = true
        audioSource.start(
            { frame, _ ->
                synchronized(lock) { if (listening) frames.add(frame) }
                level = rms(frame)
            },
            { error ->
                captureError = error.message ?: error::class.java.simpleName
                level = 0f
            },
        )
        return true
    }

    /** Stops capture and runs the whole buffer through whisper once; never throws. */
    suspend fun stop(language: String? = null): EngineResult {
        audioSource.stop()
        listening = false
        level = 0f
        val pcm = synchronized(lock) {
            val total = frames.sumOf { it.size }
            val out = FloatArray(total)
            var index = 0
            for (frame in frames) {
                for (sample in frame) out[index++] = sample / 32768f
            }
            frames.clear()
            out
        }
        val failure = captureError
        if (pcm.isEmpty()) {
            return EngineResult(
                engineId = EngineIds.WHISPER,
                text = "",
                loadMs = 0L,
                inferMs = 0L,
                audioMs = 0L,
                error = failure ?: "no audio captured",
            )
        }
        val audioMs = pcm.size * 1000L / SAMPLE_RATE
        val status = status()
        if (!status.available) {
            return EngineResult(
                engineId = EngineIds.WHISPER,
                text = "",
                loadMs = 0L,
                inferMs = 0L,
                audioMs = audioMs,
                error = status.detail ?: "engine ${status.availability.name.lowercase().replace('_', ' ')}",
            )
        }
        return CompareRunner(registry).compare(pcm, SAMPLE_RATE, language).firstOrNull()
            ?: EngineResult(
                engineId = EngineIds.WHISPER,
                text = "",
                loadMs = 0L,
                inferMs = 0L,
                audioMs = audioMs,
                error = "engine returned no result",
            )
    }

    fun cancel() {
        audioSource.stop()
        listening = false
        level = 0f
        synchronized(lock) { frames.clear() }
    }

    fun release() {
        cancel()
        audioSource.release()
    }

    private fun rms(frame: ShortArray): Float {
        if (frame.isEmpty()) return 0f
        var sum = 0.0
        for (sample in frame) {
            val value = sample / 32768.0
            sum += value * value
        }
        return (sqrt(sum / frame.size)).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        const val SAMPLE_RATE: Int = 16_000

        fun create(
            context: Context,
            settings: SettingsStore = SettingsStore(context),
            repository: ModelRepository = ModelRepositoryProvider.forContext(context),
            audioSource: AudioSource = AudioRecordSource(context),
            engineFactory: () -> SpeechEngine = { EngineFactory.create(context, settings) },
            runtimePresent: () -> Boolean = { WhisperCppEngine.isNativeAvailable },
        ): OnboardingDictation {
            val spec = settings.customModels.firstOrNull { it.id == settings.modelId }
                ?: ModelCatalog.byId(settings.modelId)
                ?: ModelCatalog.default
            return OnboardingDictation(spec, repository, audioSource, engineFactory, runtimePresent)
        }
    }
}
