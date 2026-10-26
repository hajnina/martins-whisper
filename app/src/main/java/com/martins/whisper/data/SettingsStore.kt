package com.martins.whisper.data

import android.content.Context
import android.content.SharedPreferences
import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.asr.EngineState

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var modelId: String
        get() = prefs.getString(KEY_MODEL_ID, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID
        set(value) = prefs.edit().putString(KEY_MODEL_ID, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, LANGUAGE_AUTO) ?: LANGUAGE_AUTO
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var backend: ComputeBackend
        get() = parseBackend(prefs.getString(KEY_BACKEND, ComputeBackend.AUTO.name))
        set(value) = prefs.edit().putString(KEY_BACKEND, value.name).apply()

    var engineState: EngineState
        get() = EngineState(
            activeBackend = prefs.getString(KEY_ENGINE_ACTIVE_BACKEND, null),
            fallbackReason = prefs.getString(KEY_ENGINE_FALLBACK_REASON, null),
            gpuFailureCount = prefs.getInt(KEY_ENGINE_GPU_FAILURE_COUNT, 0),
            lastError = prefs.getString(KEY_ENGINE_LAST_ERROR, null),
        )
        set(value) {
            prefs.edit()
                .putString(KEY_ENGINE_ACTIVE_BACKEND, value.activeBackend)
                .putString(KEY_ENGINE_FALLBACK_REASON, value.fallbackReason)
                .putInt(KEY_ENGINE_GPU_FAILURE_COUNT, value.gpuFailureCount.coerceAtLeast(0))
                .putString(KEY_ENGINE_LAST_ERROR, value.lastError)
                .apply()
        }

    var idleUnloadSeconds: Int
        get() = prefs.getInt(KEY_IDLE_UNLOAD_SECONDS, DEFAULT_IDLE_UNLOAD_SECONDS)
        set(value) = prefs.edit().putInt(KEY_IDLE_UNLOAD_SECONDS, value).apply()

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()

    var bootStart: Boolean
        get() = prefs.getBoolean(KEY_BOOT_START, true)
        set(value) = prefs.edit().putBoolean(KEY_BOOT_START, value).apply()

    var bubbleX: Int
        get() = prefs.getInt(KEY_BUBBLE_X, UNSET_POSITION)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_X, value).apply()

    var bubbleY: Int
        get() = prefs.getInt(KEY_BUBBLE_Y, UNSET_POSITION)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_Y, value).apply()

    fun hasSavedBubblePosition(): Boolean = bubbleX != UNSET_POSITION && bubbleY != UNSET_POSITION

    fun saveBubblePosition(x: Int, y: Int) {
        prefs.edit().putInt(KEY_BUBBLE_X, x).putInt(KEY_BUBBLE_Y, y).apply()
    }

    fun languageOrNull(): String? = language.takeUnless { it == LANGUAGE_AUTO }

    fun effectiveLanguage(): String? = languageOrNull()

    companion object {
        const val PREFS_NAME = "martins_whisper_settings"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_LANGUAGE = "language"
        const val KEY_BACKEND = "backend"
        const val KEY_ENGINE_ACTIVE_BACKEND = "engine_active_backend"
        const val KEY_ENGINE_FALLBACK_REASON = "engine_fallback_reason"
        const val KEY_ENGINE_GPU_FAILURE_COUNT = "engine_gpu_failure_count"
        const val KEY_ENGINE_LAST_ERROR = "engine_last_error"
        const val KEY_IDLE_UNLOAD_SECONDS = "idle_unload_seconds"
        const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        const val KEY_BOOT_START = "boot_start"
        const val KEY_BUBBLE_X = "bubble_x"
        const val KEY_BUBBLE_Y = "bubble_y"
        const val DEFAULT_MODEL_ID = "base"
        const val DEFAULT_IDLE_UNLOAD_SECONDS = 300
        const val LANGUAGE_AUTO = "auto"
        const val UNSET_POSITION = -1

        fun parseBackend(raw: String?): ComputeBackend = runCatching {
            ComputeBackend.valueOf(raw ?: ComputeBackend.AUTO.name)
        }.getOrDefault(ComputeBackend.AUTO)
    }
}
