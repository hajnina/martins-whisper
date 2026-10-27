package com.martins.whisper.onboarding

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4A46FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFECEBFF),
    onPrimaryContainer = Color(0xFF1C1D38),
    background = Color(0xFFF6F6F4),
    onBackground = Color(0xFF17172A),
    surface = Color(0xFFF6F6F4),
    onSurface = Color(0xFF17172A),
    surfaceVariant = Color(0xFFEDEDF3),
    onSurfaceVariant = Color(0xFF494A57),
    surfaceContainer = Color(0xFFF1F1EE),
    surfaceContainerHigh = Color(0xFFEBEBF3),
    surfaceContainerHighest = Color(0xFFE4E4EE),
    secondaryContainer = Color(0xFFD7D7FF),
    onSecondaryContainer = Color(0xFF17172A),
    outline = Color(0xFF747588),
    outlineVariant = Color(0xFFC9C9D4),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8C89FF),
    onPrimary = Color(0xFF0C0D16),
    primaryContainer = Color(0xFF1C1D38),
    onPrimaryContainer = Color(0xFFD8D7FF),
    background = Color(0xFF0C0D16),
    onBackground = Color(0xFFDCDCE6),
    surface = Color(0xFF0C0D16),
    onSurface = Color(0xFFDCDCE6),
    surfaceVariant = Color(0xFF1F2033),
    onSurfaceVariant = Color(0xFFB9B9C8),
    surfaceContainer = Color(0xFF171826),
    surfaceContainerHigh = Color(0xFF1F2033),
    surfaceContainerHighest = Color(0xFF26273B),
    secondaryContainer = Color(0xFF26273B),
    onSecondaryContainer = Color(0xFFD8D7FF),
    outline = Color(0xFF747588),
    outlineVariant = Color(0xFF33344A),
)

private val OnboardingTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 46.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

private val OnboardingShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MartinsOnboardingTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialExpressiveTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = OnboardingTypography,
        shapes = OnboardingShapes,
        content = content,
    )
}
