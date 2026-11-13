package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PulseEmerald,
    onPrimary = Color(0xFF002116),
    primaryContainer = PulseEmeraldContainerDark,
    onPrimaryContainer = Color(0xFF8FFAD3),
    secondary = LiveCoral,
    onSecondary = Color.White,
    secondaryContainer = LiveCoralContainerDark,
    onSecondaryContainer = Color(0xFFFFD9DF),
    tertiary = SignalAmber,
    onTertiary = Color(0xFF281B00),
    tertiaryContainer = SignalAmberContainerDark,
    onTertiaryContainer = Color(0xFFFFE08A),
    background = ObsidianBg,
    onBackground = SlateTextPrimary,
    surface = SlateSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = SlateTextSecondary,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateSurfaceElevated,
    surfaceContainerHigh = SlateSurfaceHighest,
    outline = SlateOutline
)

private val LightColorScheme = lightColorScheme(
    primary = PulseEmeraldDark,
    onPrimary = Color.White,
    primaryContainer = PulseEmeraldContainerLight,
    onPrimaryContainer = Color(0xFF002116),
    secondary = LiveCoralDark,
    onSecondary = Color.White,
    secondaryContainer = LiveCoralContainerLight,
    onSecondaryContainer = Color(0xFF40000D),
    tertiary = SignalAmber,
    onTertiary = Color(0xFF281B00),
    tertiaryContainer = SignalAmberContainerLight,
    onTertiaryContainer = Color(0xFF281B00),
    background = MintMistBg,
    onBackground = InkTextPrimary,
    surface = CrispWhiteSurface,
    onSurface = InkTextPrimary,
    surfaceVariant = SoftTintSurface,
    onSurfaceVariant = InkTextSecondary,
    surfaceContainerLow = CrispWhiteSurface,
    surfaceContainer = SoftTintSurface,
    surfaceContainerHigh = SoftTintHighest,
    outline = MintOutline
)

val PulseLiveShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PulseLiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PulseLiveTypography,
        shapes = PulseLiveShapes,
        content = content
    )
}
