package com.martins.whisper.data

import android.content.Context
import android.content.SharedPreferences
import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.ModelSpec
import com.martins.whisper.core.asr.CustomModelCodec
import com.martins.whisper.core.asr.EngineState
import com.martins.whisper.core.asr.LanguagePrefs

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var modelId: String
        get() = prefs.getString(KEY_MODEL_ID, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID
        set(value) = prefs.edit().putString(KEY_MODEL_ID, value).apply()

    var language: String
        get() {
            val stored = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
            return stored.takeIf { it in SUPPORTED_LANGUAGES } ?: DEFAULT_LANGUAGE
        }
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

    var customModels: List<ModelSpec>
        get() = CustomModelCodec.decode(prefs.getString(KEY_CUSTOM_MODELS, null))
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_MODELS, CustomModelCodec.encode(value)).apply()
        }

    fun addCustomModel(spec: ModelSpec) {
        customModels = customModels.filterNot { it.id == spec.id } + spec
    }

    fun removeCustomModel(id: String) {
        customModels = customModels.filterNot { it.id == id }
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

    /**
     * Primary/secondary/sticky language selection written by onboarding; null when onboarding has
     * not run yet (the legacy [language] key then remains the source of truth).
     */
    var languagePrefs: LanguagePrefs?
        get() = LanguagePrefs.decode(prefs.getString(KEY_LANGUAGE_PREFS, null))
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_LANGUAGE_PREFS)
                } else {
                    val normalized = value.normalized()
                    putString(KEY_LANGUAGE_PREFS, normalized.encode())
                    putString(KEY_LANGUAGE, normalized.primaryLanguage)
                }
            }.apply()
        }

    fun languageOrNull(): String? = language.takeUnless { it == LANGUAGE_AUTO }

    fun effectiveLanguage(): String? =
        languagePrefs?.primaryLanguage ?: languageOrNull()

    companion object {
        const val PREFS_NAME = "martins_whisper_settings"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_LANGUAGE = "language"
        const val KEY_LANGUAGE_PREFS = "language_prefs"
        const val KEY_BACKEND = "backend"
        const val KEY_ENGINE_ACTIVE_BACKEND = "engine_active_backend"
        const val KEY_ENGINE_FALLBACK_REASON = "engine_fallback_reason"
        const val KEY_ENGINE_GPU_FAILURE_COUNT = "engine_gpu_failure_count"
        const val KEY_ENGINE_LAST_ERROR = "engine_last_error"
        const val KEY_CUSTOM_MODELS = "custom_models"
        const val KEY_IDLE_UNLOAD_SECONDS = "idle_unload_seconds"
        const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        const val KEY_BOOT_START = "boot_start"
        const val KEY_BUBBLE_X = "bubble_x"
        const val KEY_BUBBLE_Y = "bubble_y"
        const val DEFAULT_MODEL_ID = "base"
        const val DEFAULT_IDLE_UNLOAD_SECONDS = 300
        const val LANGUAGE_AUTO = "auto"
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_CZECH = "cs"
        const val DEFAULT_LANGUAGE = LANGUAGE_ENGLISH
        val SUPPORTED_LANGUAGES = setOf(LANGUAGE_AUTO, LANGUAGE_ENGLISH, LANGUAGE_CZECH)
        const val UNSET_POSITION = -1

        fun parseBackend(raw: String?): ComputeBackend = runCatching {
            ComputeBackend.valueOf(raw ?: ComputeBackend.AUTO.name)
        }.getOrDefault(ComputeBackend.AUTO)
    }
}
