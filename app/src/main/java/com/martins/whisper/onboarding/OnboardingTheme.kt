package com.martins.whisper.onboarding

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.martins.whisper.ui.theme.MartinsWhisperTheme

/** Motion durations in ms; 0 when the system "remove animations" setting is on. */
data class MotionDurations(
    val enter: Int = 300,
    val exit: Int = 200,
    val state: Int = 200,
    val press: Int = 120,
    val ring: Int = 1_800,
)

val LocalMotionDurations = staticCompositionLocalOf { MotionDurations() }

@Composable
fun MartinsOnboardingTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val animationsOn = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }.getOrDefault(true)
    val durations = if (animationsOn) MotionDurations() else MotionDurations(0, 0, 0, 0, 0)
    CompositionLocalProvider(LocalMotionDurations provides durations) {
        MartinsWhisperTheme(content = content)
    }
}
