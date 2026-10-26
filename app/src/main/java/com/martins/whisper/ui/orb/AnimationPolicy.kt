package com.martins.whisper.ui.orb

import android.animation.ValueAnimator

/** Honours the system "remove animations" accessibility setting. */
object AnimationPolicy {
    fun enabled(): Boolean = try {
        ValueAnimator.areAnimatorsEnabled()
    } catch (_: Throwable) {
        true
    }
}
