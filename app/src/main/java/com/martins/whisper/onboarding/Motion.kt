package com.martins.whisper.onboarding

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Material 3 Expressive motion tokens (copied from zenmeter's Motion object).
 */
object Motion {
    fun <T> defaultSpatialSpec(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    fun <T> fastSpatialSpec(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    fun <T> slowSpatialSpec(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    fun <T> defaultEffectsSpec(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    fun <T> fastEffectsSpec(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    fun <T> slowEffectsSpec(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}

/** Wraps a click action with the light haptic used for primary buttons. */
@Composable
fun hapticClick(onClick: () -> Unit): () -> Unit {
    val feedback = LocalHapticFeedback.current
    return {
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
}

/** Fires a firmer haptic exactly when a permission flips from not granted to granted. */
@Composable
fun HapticOnGrant(granted: Boolean) {
    val feedback = LocalHapticFeedback.current
    var previous by remember { mutableStateOf(granted) }
    LaunchedEffect(granted) {
        if (granted && !previous) {
            feedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        previous = granted
    }
}
