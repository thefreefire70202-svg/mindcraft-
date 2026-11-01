package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MindcraftDarkColorScheme = darkColorScheme(
    primary = DiamondBlue,
    onPrimary = Color(0xFF00354E),
    primaryContainer = Color(0xFF074766),
    onPrimaryContainer = Color(0xFFCBE6FF),
    secondary = EmeraldGreen,
    onSecondary = Color(0xFF003823),
    secondaryContainer = Color(0xFF065335),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = GoldYellow,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF633F00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    error = RedstoneRed,
    onError = Color.White,
    errorContainer = Color(0xFF4B1113),
    onErrorContainer = Color(0xFFFFDAD6),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    outlineVariant = Color(0xFF1E2A38)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to true for immersive Minecraft console HUD
    dynamicColor: Boolean = false, // Keep clean brand dark aesthetic
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MindcraftDarkColorScheme,
        typography = Typography,
        content = content
    )
}
