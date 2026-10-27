package com.martins.whisper.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

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
