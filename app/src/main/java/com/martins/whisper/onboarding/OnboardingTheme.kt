package com.martins.whisper.onboarding

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CalmTeal = Color(0xFF135D66)
private val CalmTealNight = Color(0xFF8FD3DC)
private val TrueDark = Color(0xFF0B0F10)
private val TrueDarkSurface = Color(0xFF121719)
private val LightSurface = Color(0xFFFFFBF7)

private val LightScheme = lightColorScheme(
    primary = CalmTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3ECEF),
    onPrimaryContainer = Color(0xFF00363C),
    secondaryContainer = Color(0xFFCCE8EA),
    onSecondaryContainer = Color(0xFF051F21),
    background = LightSurface,
    onBackground = Color(0xFF1A1C1E),
    surface = LightSurface,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEDF3F4),
    onSurfaceVariant = Color(0xFF43474B),
    surfaceContainer = Color(0xFFF4F0EC),
    surfaceContainerHigh = Color(0xFFEEE9E4),
    outline = Color(0xFF73777A),
    outlineVariant = Color(0xFFC4C7C5),
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = CalmTealNight,
    onPrimary = Color(0xFF00363C),
    primaryContainer = Color(0xFF004F56),
    onPrimaryContainer = Color(0xFFA8EDF3),
    secondaryContainer = Color(0xFF324B4D),
    onSecondaryContainer = Color(0xFFCCE8EA),
    background = TrueDark,
    onBackground = Color(0xFFE2E3E5),
    surface = TrueDark,
    onSurface = Color(0xFFE2E3E5),
    surfaceVariant = Color(0xFF1B2225),
    onSurfaceVariant = Color(0xFFC2C7CB),
    surfaceContainer = TrueDarkSurface,
    surfaceContainerHigh = Color(0xFF1B2225),
    outline = Color(0xFF8D9194),
    outlineVariant = Color(0xFF3F484A),
    error = Color(0xFFFFB4AB),
)

private fun bold(size: Int, height: Int) =
    TextStyle(fontWeight = FontWeight.SemiBold, fontSize = size.sp, lineHeight = height.sp)

private val OnboardingTypography = Typography(
    displaySmall = bold(36, 44),
    headlineMedium = bold(28, 36),
    titleMedium = bold(18, 24),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = bold(14, 20),
)

private val OnboardingShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

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
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    val animationsOn = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }.getOrDefault(true)
    val durations = if (animationsOn) MotionDurations() else MotionDurations(0, 0, 0, 0, 0)
    CompositionLocalProvider(LocalMotionDurations provides durations) {
        MaterialTheme(
            colorScheme = scheme,
            typography = OnboardingTypography,
            shapes = OnboardingShapes,
            content = content,
        )
    }
}
