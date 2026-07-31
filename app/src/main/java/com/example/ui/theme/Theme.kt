package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

private val NeonLimeColorScheme = darkColorScheme(
    primary = NeonLime,
    onPrimary = Color.Black,
    primaryContainer = DarkLimeContainer,
    onPrimaryContainer = LimeOnContainer,
    secondary = ElectricCyan,
    onSecondary = Color.Black,
    secondaryContainer = DarkCyanContainer,
    onSecondaryContainer = ElectricCyan,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    error = PulseRed,
    onError = Color.White
)

private val NeonLimeLightColorScheme = lightColorScheme(
    primary = NeonLime,
    onPrimary = Color.Black,
    primaryContainer = LightLimeContainer,
    onPrimaryContainer = LightLimeOnContainer,
    secondary = ElectricCyan,
    onSecondary = Color.Black,
    secondaryContainer = LightCyanContainer,
    onSecondaryContainer = LightCyanOnContainer,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightCardBorder,
    error = PulseRed,
    onError = Color.White
)

private val ElectricCyanColorScheme = NeonLimeColorScheme.copy(
    primary = ElectricCyan,
    primaryContainer = DarkCyanContainer,
    onPrimaryContainer = Color(0xFF80F2FF),
    secondary = NeonLime,
    secondaryContainer = DarkLimeContainer,
    onSecondaryContainer = LimeOnContainer
)

private val ElectricCyanLightColorScheme = NeonLimeLightColorScheme.copy(
    primary = ElectricCyan,
    primaryContainer = LightCyanContainer,
    onPrimaryContainer = LightCyanOnContainer,
    secondary = NeonLime,
    secondaryContainer = LightLimeContainer,
    onSecondaryContainer = LightLimeOnContainer
)

private val PulseRedColorScheme = NeonLimeColorScheme.copy(
    primary = PulseRed,
    primaryContainer = DarkRedContainer,
    onPrimaryContainer = RedOnContainer,
    secondary = MutedAmber,
    secondaryContainer = DarkAmberContainer,
    onSecondaryContainer = AmberOnContainer
)

private val PulseRedLightColorScheme = NeonLimeLightColorScheme.copy(
    primary = PulseRed,
    primaryContainer = LightRedContainer,
    onPrimaryContainer = LightRedOnContainer,
    secondary = MutedAmber,
    secondaryContainer = LightAmberContainer,
    onSecondaryContainer = LightAmberOnContainer
)

private val MutedAmberColorScheme = NeonLimeColorScheme.copy(
    primary = MutedAmber,
    primaryContainer = DarkAmberContainer,
    onPrimaryContainer = AmberOnContainer,
    secondary = ElectricCyan,
    secondaryContainer = DarkCyanContainer,
    onSecondaryContainer = Color(0xFF80F2FF)
)

private val MutedAmberLightColorScheme = NeonLimeLightColorScheme.copy(
    primary = MutedAmber,
    primaryContainer = LightAmberContainer,
    onPrimaryContainer = LightAmberOnContainer,
    secondary = ElectricCyan,
    secondaryContainer = LightCyanContainer,
    onSecondaryContainer = LightCyanOnContainer
)

private val SoftGreenColorScheme = NeonLimeColorScheme.copy(
    primary = SoftGreen,
    primaryContainer = DarkGreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary = MutedAmber,
    secondaryContainer = DarkAmberContainer,
    onSecondaryContainer = AmberOnContainer
)

private val SoftGreenLightColorScheme = NeonLimeLightColorScheme.copy(
    primary = SoftGreen,
    primaryContainer = LightGreenContainer,
    onPrimaryContainer = LightGreenOnContainer,
    secondary = MutedAmber,
    secondaryContainer = LightAmberContainer,
    onSecondaryContainer = LightAmberOnContainer
)

@Composable
fun PacePulseTheme(
    content: @Composable () -> Unit
) {
    val currentTheme by ThemeManager.currentTheme.collectAsState()
    val currentThemeMode by ThemeManager.currentThemeMode.collectAsState()
    
    val systemInDarkTheme = isSystemInDarkTheme()
    val isDark = when (currentThemeMode) {
        ThemeMode.SYSTEM -> systemInDarkTheme
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = when (currentTheme) {
        AppTheme.NEON_LIME -> if (isDark) NeonLimeColorScheme else NeonLimeLightColorScheme
        AppTheme.ELECTRIC_CYAN -> if (isDark) ElectricCyanColorScheme else ElectricCyanLightColorScheme
        AppTheme.PULSE_RED -> if (isDark) PulseRedColorScheme else PulseRedLightColorScheme
        AppTheme.MUTED_AMBER -> if (isDark) MutedAmberColorScheme else MutedAmberLightColorScheme
        AppTheme.SOFT_GREEN -> if (isDark) SoftGreenColorScheme else SoftGreenLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

