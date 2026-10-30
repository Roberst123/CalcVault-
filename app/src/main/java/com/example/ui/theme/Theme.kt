package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CalcVaultDarkColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = ObsidianBlack,
    primaryContainer = GoldContainer,
    onPrimaryContainer = GoldOnContainer,
    secondary = CyanAccent,
    onSecondary = ObsidianBlack,
    secondaryContainer = Color(0xFF0F2936),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = EmeraldSuccess,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = TextSecondary,
    outline = CharcoalBorder,
    error = CrimsonError,
    onError = Color.White
)

private val CalcVaultLightColorScheme = lightColorScheme(
    primary = GoldAccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    background = Color(0xFFF3F4F6),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB),
    error = CrimsonError,
    onError = Color.White
)

@Composable
fun CalcVaultTheme(
    darkTheme: Boolean = true, // Default to sleek stealth dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CalcVaultDarkColorScheme else CalcVaultLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = CalcVaultTheme(darkTheme = true, content = content)
