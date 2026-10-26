package com.martins.whisper.core

/**
 * Default-OFF switches for work that is still in flight. Master stays releasable:
 * anything unfinished ships dark here and turns on only when its lane passes acceptance.
 *
 * Keep this object tiny and boring. Flip a flag for a debug build or a test, never
 * make the user-visible default depend on an unproven runtime.
 */
object Flags {
    @Volatile
    var onboardingV2: Boolean = false

    @Volatile
    var gemmaEngine: Boolean = false

    @Volatile
    var accelBackends: Boolean = false
}
