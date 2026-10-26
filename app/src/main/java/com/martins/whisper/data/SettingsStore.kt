package com.martins.whisper.data

import android.content.Context
import android.content.SharedPreferences
import com.martins.whisper.asr.EngineDiagnostics
import com.martins.whisper.core.ComputeBackend

class SettingsStore(context: Context) : EngineDiagnostics {

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

    override var lastModelId: String?
        get() = prefs.getString(KEY_LAST_MODEL_ID, null)
        set(value) = prefs.edit().putString(KEY_LAST_MODEL_ID, value).apply()

    override var lastBackend: String?
        get() = prefs.getString(KEY_LAST_BACKEND, null)
        set(value) = prefs.edit().putString(KEY_LAST_BACKEND, value).apply()

    override var lastFallbackReason: String?
        get() = prefs.getString(KEY_LAST_FALLBACK_REASON, null)
        set(value) = prefs.edit().putString(KEY_LAST_FALLBACK_REASON, value).apply()

    override var gpuFailureCount: Int
        get() = prefs.getInt(KEY_GPU_FAILURE_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_GPU_FAILURE_COUNT, value.coerceAtLeast(0)).apply()

    override var lastEngineError: String?
        get() = prefs.getString(KEY_LAST_ENGINE_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_ENGINE_ERROR, value).apply()

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
        const val KEY_IDLE_UNLOAD_SECONDS = "idle_unload_seconds"
        const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        const val KEY_BOOT_START = "boot_start"
        const val KEY_BUBBLE_X = "bubble_x"
        const val KEY_BUBBLE_Y = "bubble_y"
        const val KEY_LAST_MODEL_ID = "last_model_id"
        const val KEY_LAST_BACKEND = "last_backend"
        const val KEY_LAST_FALLBACK_REASON = "last_fallback_reason"
        const val KEY_GPU_FAILURE_COUNT = "gpu_failure_count"
        const val KEY_LAST_ENGINE_ERROR = "last_engine_error"
        const val DEFAULT_MODEL_ID = "base"
        const val DEFAULT_IDLE_UNLOAD_SECONDS = 300
        const val LANGUAGE_AUTO = "auto"
        const val UNSET_POSITION = -1

        fun parseBackend(raw: String?): ComputeBackend = runCatching {
            ComputeBackend.valueOf(raw ?: ComputeBackend.AUTO.name)
        }.getOrDefault(ComputeBackend.AUTO)
    }
}
