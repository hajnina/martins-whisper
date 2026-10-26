package com.martins.whisper.core.engine

import com.martins.whisper.core.SpeechEngine

/** Stable identifiers shared with settings, telemetry and the sidecar JSON. */
object EngineIds {
    const val WHISPER: String = "whisper"
    const val GEMMA_E2B: String = "gemma-e2b"

    /** Display order used when no persisted default exists (whisper stays the safe default). */
    val ORDER: List<String> = listOf(WHISPER, GEMMA_E2B)

    fun isKnown(id: String): Boolean = id in ORDER
}

enum class EngineAvailability {
    /** Runtime and model are present; the engine may still fail when opened. */
    AVAILABLE,

    /** The on-device runtime (native library / LiteRT / MediaPipe) is missing. */
    RUNTIME_MISSING,

    /** The runtime exists but the model file has not been downloaded/imported. */
    MODEL_MISSING,

    /** The probe itself failed; [EngineStatus.detail] carries the reason. */
    ERROR,
}

/**
 * What the app knows about one engine without loading it. Both facts are exposed separately
 * because onboarding shows "download the model" or "this build has no runtime" as different
 * actions.
 */
data class EngineStatus(
    val engineId: String,
    val displayName: String,
    val modelInstalled: Boolean,
    val runtimePresent: Boolean,
    val availability: EngineAvailability,
    val detail: String? = null,
) {
    val available: Boolean
        get() = availability == EngineAvailability.AVAILABLE

    companion object {
        fun of(
            engineId: String,
            displayName: String,
            modelInstalled: Boolean,
            runtimePresent: Boolean,
            error: String? = null,
        ): EngineStatus = EngineStatus(
            engineId = engineId,
            displayName = displayName,
            modelInstalled = modelInstalled,
            runtimePresent = runtimePresent,
            availability = when {
                !error.isNullOrBlank() -> EngineAvailability.ERROR
                !runtimePresent -> EngineAvailability.RUNTIME_MISSING
                !modelInstalled -> EngineAvailability.MODEL_MISSING
                else -> EngineAvailability.AVAILABLE
            },
            detail = error,
        )
    }
}

/** Raw probe result filled in by the app wiring for one engine. */
data class EngineProbe(
    val modelInstalled: Boolean,
    val runtimePresent: Boolean,
    val error: String? = null,
)

/**
 * One engine wired into the registry. [probe] must be cheap and side-effect free (a file check,
 * not a model load); [open] builds a ready-to-use [SpeechEngine] and may throw.
 */
class EngineRegistration(
    val id: String,
    val displayName: String,
    private val probe: () -> EngineProbe,
    private val factory: () -> SpeechEngine,
) {
    fun status(): EngineStatus {
        val result = probe()
        return EngineStatus.of(id, displayName, result.modelInstalled, result.runtimePresent, result.error)
    }

    fun open(): SpeechEngine = factory()
}

/** Persistence hook: SharedPreferences in the app, a map in tests. */
interface EnginePreferences {
    /** Raw persisted default; null means "never chosen". */
    var defaultEngineId: String?
}

class InMemoryEnginePreferences(initial: String? = null) : EnginePreferences {
    override var defaultEngineId: String? = initial
}

/**
 * The selected engine for one dictation after applying the persisted default and the
 * per-dictation override. [fallbackReason] is non-null when the requested engine was not used.
 */
data class EngineResolution(
    val engineId: String,
    val status: EngineStatus,
    val fallbackReason: String? = null,
)

/**
 * Registry over the existing [SpeechEngine] contract.
 *
 * Resolution order for a dictation:
 * 1. the per-dictation override, when registered and available;
 * 2. the persisted default, when registered and available;
 * 3. the first available engine in [EngineIds.ORDER].
 *
 * The persisted default is kept even while its model is missing so a download does not lose the
 * user's choice; [resolve] reports the temporary fallback instead.
 */
class EngineRegistry(
    registrations: List<EngineRegistration>,
    private val preferences: EnginePreferences = InMemoryEnginePreferences(),
) {
    private val registrations: List<EngineRegistration> = registrations.toList()
    private val byId: Map<String, EngineRegistration>

    init {
        val duplicate = this.registrations.groupBy { it.id }.entries.firstOrNull { it.value.size > 1 }
        require(duplicate == null) { "duplicate engine id: ${duplicate?.key}" }
        this.registrations.forEach { registration ->
            require(registration.id.isNotBlank()) { "engine id must not be blank" }
        }
        byId = this.registrations.associateBy { it.id }
    }

    fun ids(): List<String> = registrations.map { it.id }

    fun status(engineId: String): EngineStatus? = byId[engineId]?.status()

    fun statuses(): List<EngineStatus> = registrations.map { it.status() }

    fun available(): List<EngineStatus> = statuses().filter { it.available }

    /** Raw persisted default, which may point at an engine whose model is not installed. */
    fun storedDefaultEngineId(): String? = preferences.defaultEngineId

    /**
     * Effective default when no per-dictation override is given: the persisted engine when it is
     * available, otherwise the first available engine, otherwise the persisted/first registered id.
     */
    fun defaultEngineId(): String {
        val stored = preferences.defaultEngineId
        stored?.let { status(it) }?.takeIf { it.available }?.let { return it.engineId }
        available().firstOrNull()?.let { return it.engineId }
        stored?.let { status(it) }?.let { return it.engineId }
        return registrations.firstOrNull()?.id ?: EngineIds.WHISPER
    }

    /** Persists an explicit user choice. Returns false for an unknown engine id. */
    fun setDefaultEngine(engineId: String): Boolean {
        if (byId[engineId] == null) return false
        preferences.defaultEngineId = engineId
        return true
    }

    /** Applies [override] for one dictation; never mutates the persisted default. */
    fun resolve(override: String? = null): EngineResolution {
        val requested = override?.takeIf { it.isNotBlank() }
        if (requested != null) {
            val status = status(requested)
            if (status != null && status.available) {
                return EngineResolution(status.engineId, status)
            }
            val fallback = fallback()
            val reason = if (status == null) {
                "unknown engine '$requested'"
            } else {
                "engine '${status.engineId}' is ${status.availability.name.lowercase().replace('_', ' ')}"
            }
            return EngineResolution(fallback.engineId, fallback, reason)
        }

        val stored = preferences.defaultEngineId
        val defaultId = defaultEngineId()
        val defaultStatus = status(defaultId)
        val fallbackReason = when {
            defaultStatus == null -> "default engine '$defaultId' is not registered"
            !defaultStatus.available -> "default engine '${defaultStatus.engineId}' is " +
                defaultStatus.availability.name.lowercase().replace('_', ' ')
            stored != null && stored != defaultId -> {
                val storedStatus = status(stored)
                "default engine '$stored' is " +
                    (storedStatus?.availability?.name?.lowercase()?.replace('_', ' ') ?: "not registered")
            }
            else -> null
        }
        return EngineResolution(
            engineId = defaultId,
            status = defaultStatus ?: missingDefault(defaultId),
            fallbackReason = fallbackReason,
        )
    }

    fun open(engineId: String): SpeechEngine =
        byId[engineId]?.open() ?: throw NoSuchElementException("Unknown engine id: $engineId")

    private fun fallback(): EngineStatus {
        available().firstOrNull()?.let { return it }
        val defaultId = defaultEngineId()
        return status(defaultId) ?: missingDefault(defaultId)
    }

    private fun missingDefault(engineId: String): EngineStatus =
        EngineStatus.of(engineId, engineId, modelInstalled = false, runtimePresent = false)
}
