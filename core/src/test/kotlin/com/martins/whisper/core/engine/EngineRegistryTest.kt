package com.martins.whisper.core.engine

import com.martins.whisper.core.asr.FakeSpeechEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineRegistryTest {

    private fun registration(
        id: String,
        modelInstalled: Boolean = true,
        runtimePresent: Boolean = true,
        engine: () -> com.martins.whisper.core.SpeechEngine = { FakeSpeechEngine(backend = id) },
    ) = EngineRegistration(
        id = id,
        displayName = id,
        probe = { EngineProbe(modelInstalled = modelInstalled, runtimePresent = runtimePresent) },
        factory = engine,
    )

    private fun registry(
        preferences: EnginePreferences = InMemoryEnginePreferences(),
        registrations: List<EngineRegistration>,
    ) = EngineRegistry(registrations, preferences)

    @Test
    fun `status reports model and runtime separately`() {
        val registry = registry(
            registrations = listOf(
                registration(EngineIds.WHISPER, modelInstalled = true, runtimePresent = false),
                registration(EngineIds.GEMMA_E2B, modelInstalled = false, runtimePresent = true),
            ),
        )

        val whisper = registry.status(EngineIds.WHISPER)!!
        val gemma = registry.status(EngineIds.GEMMA_E2B)!!

        assertTrue(whisper.modelInstalled)
        assertFalse(whisper.runtimePresent)
        assertEquals(EngineAvailability.RUNTIME_MISSING, whisper.availability)
        assertFalse(gemma.modelInstalled)
        assertTrue(gemma.runtimePresent)
        assertEquals(EngineAvailability.MODEL_MISSING, gemma.availability)
        assertTrue(registry.available().isEmpty())
        // Mutation killed: merging the two facts into one "ready" boolean, which would show
        // "download the model" when the APK actually lacks the runtime (or the reverse).
    }

    @Test
    fun `probe error marks the engine ERROR with its detail`() {
        val broken = EngineRegistration(
            id = EngineIds.GEMMA_E2B,
            displayName = "Gemma",
            probe = { EngineProbe(modelInstalled = true, runtimePresent = true, error = "bad runtime ABI") },
            factory = { FakeSpeechEngine() },
        )
        val status = registry(registrations = listOf(broken)).status(EngineIds.GEMMA_E2B)!!

        assertEquals(EngineAvailability.ERROR, status.availability)
        assertEquals("bad runtime ABI", status.detail)
        // Mutation killed: swallowing probe failures and reporting the engine as available.
    }

    @Test
    fun `persisted default survives a missing model and resolve falls back with a reason`() {
        val preferences = InMemoryEnginePreferences(EngineIds.GEMMA_E2B)
        val registry = registry(
            preferences,
            listOf(
                registration(EngineIds.WHISPER),
                registration(EngineIds.GEMMA_E2B, modelInstalled = false),
            ),
        )

        assertEquals(EngineIds.GEMMA_E2B, registry.storedDefaultEngineId())
        assertEquals(EngineIds.WHISPER, registry.defaultEngineId())
        val resolution = registry.resolve()

        assertEquals(EngineIds.WHISPER, resolution.engineId)
        assertEquals("default engine 'gemma-e2b' is model missing", resolution.fallbackReason)
        assertEquals(EngineIds.GEMMA_E2B, preferences.defaultEngineId)
        // Mutation killed: clearing/overwriting the persisted choice when the model is absent.
    }

    @Test
    fun `per-dictation override wins and never changes the persisted default`() {
        val preferences = InMemoryEnginePreferences(EngineIds.WHISPER)
        val registry = registry(
            preferences,
            listOf(
                registration(EngineIds.WHISPER),
                registration(EngineIds.GEMMA_E2B),
            ),
        )

        val resolution = registry.resolve(override = EngineIds.GEMMA_E2B)

        assertEquals(EngineIds.GEMMA_E2B, resolution.engineId)
        assertNull(resolution.fallbackReason)
        assertEquals(EngineIds.WHISPER, preferences.defaultEngineId)
        // Mutation killed: persisting the one-shot override (the next dictation would silently
        // switch engines) or ignoring the override override entirely.
    }

    @Test
    fun `unavailable or unknown override falls back to the default with a reason`() {
        val preferences = InMemoryEnginePreferences(EngineIds.WHISPER)
        val registry = registry(
            preferences,
            listOf(
                registration(EngineIds.WHISPER),
                registration(EngineIds.GEMMA_E2B, runtimePresent = false),
            ),
        )

        val unavailable = registry.resolve(override = EngineIds.GEMMA_E2B)
        val unknown = registry.resolve(override = "cloud-magic")

        assertEquals(EngineIds.WHISPER, unavailable.engineId)
        assertEquals("engine 'gemma-e2b' is runtime missing", unavailable.fallbackReason)
        assertEquals(EngineIds.WHISPER, unknown.engineId)
        assertEquals("unknown engine 'cloud-magic'", unknown.fallbackReason)
        // Mutation killed: returning an unavailable/unknown engine id from resolve(), which
        // would make dictation try to load a runtime or model that is not there.
    }

    @Test
    fun `setDefaultEngine persists registered ids and rejects unknown ones`() {
        val preferences = InMemoryEnginePreferences()
        val registry = registry(
            preferences,
            listOf(
                registration(EngineIds.WHISPER),
                registration(EngineIds.GEMMA_E2B),
            ),
        )

        assertTrue(registry.setDefaultEngine(EngineIds.GEMMA_E2B))
        assertEquals(EngineIds.GEMMA_E2B, preferences.defaultEngineId)
        assertFalse(registry.setDefaultEngine("cloud-magic"))
        assertEquals(EngineIds.GEMMA_E2B, preferences.defaultEngineId)
        // Mutation killed: persisting arbitrary strings into settings (bricking the next start).
    }
}
