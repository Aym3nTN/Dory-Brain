package com.dorybrain.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dorybrain.shared.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = VioletSurface,
    onPrimaryContainer = VioletPressed,
    secondary = Violet,
    onSecondary = Color.White,
    background = CanvasLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = CanvasLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
    error = Danger,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = VioletLight,
    onPrimary = Color(0xFF1E1033),
    primaryContainer = Color(0xFF2A1D45),
    onPrimaryContainer = VioletLight,
    secondary = VioletLight,
    onSecondary = Color(0xFF1E1033),
    background = CanvasDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceDarkElevated,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = Danger,
    onError = Color.White
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** True when [themeMode] resolves to the dark scheme on this platform. */
@Composable
fun isDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * The shared look, used by both the Android app and the desktop app.
 *
 * Material You dynamic colour is deliberately absent: the palette is a fixed
 * brand look, and dynamic colour would repaint it from the device wallpaper.
 */
@Composable
fun DoryBrainTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = isDarkTheme(themeMode)

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
