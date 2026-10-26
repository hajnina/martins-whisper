package com.martins.whisper.onboarding

import android.content.Context
import com.martins.whisper.data.SettingsStore

/**
 * Lane 3 (UX) onboarding contract. Pure data + persistence so the flow can be tested
 * without Compose and later steps can be appended without touching existing ones.
 */
enum class OnboardingStep {
    WELCOME,
    ;

    val index: Int get() = ordinal

    companion object {
        val total: Int = entries.size
    }
}

object OnboardingRules {
    fun stepAfter(step: OnboardingStep): OnboardingStep? =
        OnboardingStep.entries.getOrNull(step.ordinal + 1)

    fun stepBefore(step: OnboardingStep): OnboardingStep? =
        OnboardingStep.entries.getOrNull(step.ordinal - 1)

    fun progressLabel(step: OnboardingStep): String =
        "${step.index + 1}/${OnboardingStep.total}"
}

class OnboardingStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(SettingsStore.PREFS_NAME, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)

    fun markCompleted() = prefs.edit().putBoolean(KEY_COMPLETED, true).apply()

    fun reset() = prefs.edit().remove(KEY_COMPLETED).apply()

    companion object {
        const val KEY_COMPLETED = "onboarding_completed_v1"
    }
}
