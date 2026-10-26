package com.martins.whisper.service

import android.content.Context
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.data.SettingsStore

/**
 * Single seam for E1: replace the body with WhisperCppEngine/NativeModelLoader.
 * Keep the contract (suspend load is optional for real engines, close releases native memory).
 */
object EngineFactory {
    @Volatile
    var factory: (Context, SettingsStore) -> SpeechEngine = { _, _ -> FakeSpeechEngine() }

    fun create(context: Context, settings: SettingsStore): SpeechEngine =
        factory(context.applicationContext, settings)
}
