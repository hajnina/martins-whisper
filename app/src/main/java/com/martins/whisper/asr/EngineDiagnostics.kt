package com.martins.whisper.asr

/**
 * Mutable diagnostics sink for the engine factory. [com.martins.whisper.data.SettingsStore]
 * implements it with SharedPreferences; tests use a plain in-memory implementation.
 */
interface EngineDiagnostics {
    var lastModelId: String?
    var lastBackend: String?
    var lastFallbackReason: String?
    var gpuFailureCount: Int
    var lastEngineError: String?
}
