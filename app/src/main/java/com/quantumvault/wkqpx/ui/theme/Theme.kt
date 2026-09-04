package com.quantumvault.wkqpx.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VaultColorScheme = darkColorScheme(
    primary = VaultPrimaryCyan,
    onPrimary = Color(0xFF03070C),
    secondary = VaultSecondaryNeonBlue,
    onSecondary = Color(0xFF03070C),
    tertiary = VaultNeonPurple,
    onTertiary = Color.White,
    background = VaultDarkBackground,
    onBackground = VaultTextPrimary,
    surface = VaultSurface,
    onSurface = VaultTextPrimary,
    surfaceVariant = VaultSurfaceVariant,
    onSurfaceVariant = VaultTextSecondary,
    outline = VaultBorderGlow,
    error = VaultErrorRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VaultColorScheme,
        typography = Typography,
        content = content
    )
}
