package com.martins.whisper.core

/**
 * Cross-lane feature flags (trunk-based development, owner/ROADMAP.md).
 *
 * Trunk is always releasable: unfinished work ships behind a flag that is **default OFF**, and
 * the user-visible default path never changes until the lane's acceptance passes. A lane may set
 * a flag in tests or a debug-only path; nothing here reads Android APIs, so every module can use
 * it. Wiring a flag to BuildConfig/DataStore is per-lane and must preserve the OFF default.
 *
 * Naming: one flag per user-visible capability, not per implementation detail.
 */
object Flags {

    /** Lane 3 (UX): new onboarding flow (Compose), replaces the current settings-first screen. */
    @Volatile
    var onboardingV2: Boolean = false

    /** Lane 2 (LANGUAGES): language prefs + full catalog + script-safe insertion. */
    @Volatile
    var languagesV2: Boolean = false

    /** Lane 4 (ENGINES): engine registry + compare flow; Gemma card may be a stub until B4. */
    @Volatile
    var gemmaEngine: Boolean = false

    /** Lane 5 (ACCEL): per-backend whisper builds and backend selection/benchmark UI. */
    @Volatile
    var accelBackends: Boolean = false

    /** Diagnostics view listing every flag and its value (release captain keeps this honest). */
    fun snapshot(): Map<String, Boolean> = linkedMapOf(
        "onboardingV2" to onboardingV2,
        "languagesV2" to languagesV2,
        "gemmaEngine" to gemmaEngine,
        "accelBackends" to accelBackends,
    )

    /** Restores every flag to its default-OFF state (tests; never called on the default path). */
    fun reset() {
        onboardingV2 = false
        languagesV2 = false
        gemmaEngine = false
        accelBackends = false
    }
}
