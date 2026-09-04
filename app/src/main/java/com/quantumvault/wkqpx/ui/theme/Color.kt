package com.quantumvault.wkqpx.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Futuristic Void & Cyber Backgrounds
val VaultDarkBackground = Color(0xFF06090E)      // Deepest Quantum Void Black
val VaultSurface = Color(0xFF0C121C)             // Dark Cyber Slate
val VaultSurfaceVariant = Color(0xFF141E2E)      // Cyber Panel Surface
val VaultSurfaceGlass = Color(0xCC0E1726)        // Glassmorphism Frosted Cyber Surface
val VaultBorder = Color(0xFF1B3148)               // Metallic Cyan Border
val VaultBorderGlow = Color(0xFF00E5FF)           // Neon Cyan Glow Border

// Electric Futuristic Cyber Palette
val VaultPrimaryCyan = Color(0xFF00F5D4)          // Electric Cyan / Neon Teal
val VaultSecondaryNeonBlue = Color(0xFF00B4D8)    // Deep Electric Blue
val VaultSecondaryBlue = VaultSecondaryNeonBlue
val VaultNeonPurple = Color(0xFF9D4EDD)           // Cyber Violet / Electric Purple
val VaultNeonPink = Color(0xFFFF007A)             // Plasma Magenta
val VaultAccentGold = Color(0xFFFFD166)           // Holographic Amber Gold
val VaultNeonGreen = Color(0xFF00FF87)            // Matrix Emerald Green

val VaultTextPrimary = Color(0xFFF8FAFC)          // Bright Quantum White
val VaultTextSecondary = Color(0xFF94A3B8)        // Steel Blue Grey
val VaultErrorRed = Color(0xFFFF3366)             // Cyber Crimson Alert

// Futuristic Gradients
val CyberNeonGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF00F5D4), Color(0xFF00B4D8), Color(0xFF9D4EDD))
)

val CyberPlasmaGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFF007A), Color(0xFF9D4EDD), Color(0xFF00F5D4))
)

val CyberEmeraldGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF00FF87), Color(0xFF00F5D4), Color(0xFF00B4D8))
)

val CyberBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF080D15),
        Color(0xFF04070B),
        Color(0xFF090814)
    )
)

val CyberHeaderGradient = Brush.linearGradient(
    colors = listOf(
        Color(0x2000F5D4),
        Color(0x209D4EDD),
        Color(0x00000000)
    )
)

val CyberCardGlowGradient = Brush.linearGradient(
    colors = listOf(
        Color(0x3300F5D4),
        Color(0x339D4EDD)
    )
)
