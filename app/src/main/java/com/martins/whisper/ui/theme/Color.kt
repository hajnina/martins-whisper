package com.martins.whisper.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Black = Color(0xFF07070F)
private val White = Color(0xFFFFFFFF)
private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorOnLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val ErrorOnContainerLight = Color(0xFF410002)
private val ErrorDark = Color(0xFFFFB4AB)
private val ErrorOnDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val ErrorOnContainerDark = Color(0xFFFFDAD6)

private val IndigoBrand = Color(0xFF4A46FF)
private val IndigoDeep = Color(0xFF1C1D38)
private val IndigoBright = Color(0xFF8C89FF)
private val IndigoLavender = Color(0xFFD8D7FF)
private val IndigoTint = Color(0xFFECEBFF)

private val TealAccent = Color(0xFF5EEAD4)
private val TealDeep = Color(0xFF06363B)
private val TealInk = Color(0xFF04211F)
private val TealPale = Color(0xFFF3FAFA)

private val NeutralPaper = Color(0xFFF6F6F4)
private val NeutralInk = Color(0xFF17172A)
private val NeutralLightInk = Color(0xFFDCDCE6)

private val SecondaryIndigoLight = Color(0xFF4B4E9B)
private val SecondaryIndigoDark = Color(0xFFB3B4FF)
private val TertiaryTealLight = Color(0xFF008A80)

val WhisperLightColorScheme = lightColorScheme(
    primary = IndigoBrand,
    onPrimary = White,
    primaryContainer = IndigoTint,
    onPrimaryContainer = IndigoDeep,
    inversePrimary = IndigoBright,
    secondary = SecondaryIndigoLight,
    onSecondary = White,
    secondaryContainer = Color(0xFFD7D7FF),
    onSecondaryContainer = NeutralInk,
    tertiary = TertiaryTealLight,
    onTertiary = White,
    tertiaryContainer = TealAccent,
    onTertiaryContainer = TealInk,
    background = NeutralPaper,
    onBackground = NeutralInk,
    surface = NeutralPaper,
    onSurface = NeutralInk,
    surfaceVariant = Color(0xFFEDEDF3),
    onSurfaceVariant = Color(0xFF494A57),
    surfaceDim = Color(0xFFD8D8DF),
    surfaceBright = Color(0xFFFCFCFB),
    surfaceContainerLowest = White,
    surfaceContainerLow = Color(0xFFF2F2EF),
    surfaceContainer = Color(0xFFF1F1EE),
    surfaceContainerHigh = Color(0xFFEBEBF3),
    surfaceContainerHighest = Color(0xFFE4E4EE),
    outline = Color(0xFF747588),
    outlineVariant = Color(0xFFC9C9D4),
    error = ErrorLight,
    onError = ErrorOnLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = ErrorOnContainerLight,
    inverseSurface = Color(0xFF2F2F46),
    inverseOnSurface = Color(0xFFF1F1F8),
    scrim = Color(0xFF000000),
)

val WhisperDarkColorScheme = darkColorScheme(
    primary = IndigoBright,
    onPrimary = Color(0xFF0C0D16),
    primaryContainer = IndigoDeep,
    onPrimaryContainer = IndigoLavender,
    inversePrimary = IndigoBrand,
    secondary = SecondaryIndigoDark,
    onSecondary = Color(0xFF0C0D16),
    secondaryContainer = Color(0xFF26273B),
    onSecondaryContainer = IndigoLavender,
    tertiary = TealAccent,
    onTertiary = TealInk,
    tertiaryContainer = TealDeep,
    onTertiaryContainer = TealPale,
    background = Color(0xFF0C0D16),
    onBackground = NeutralLightInk,
    surface = Color(0xFF0C0D16),
    onSurface = NeutralLightInk,
    surfaceVariant = Color(0xFF1F2033),
    onSurfaceVariant = Color(0xFFB9B9C8),
    surfaceDim = Color(0xFF0C0D16),
    surfaceBright = Color(0xFF3B3C55),
    surfaceContainerLowest = Black,
    surfaceContainerLow = Color(0xFF12131E),
    surfaceContainer = Color(0xFF171826),
    surfaceContainerHigh = Color(0xFF1F2033),
    surfaceContainerHighest = Color(0xFF26273B),
    outline = Color(0xFF747588),
    outlineVariant = Color(0xFF33344A),
    error = ErrorDark,
    onError = ErrorOnDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorOnContainerDark,
    inverseSurface = NeutralLightInk,
    inverseOnSurface = Color(0xFF0C0D16),
    scrim = Color(0xFF000000),
)
