package com.dorybrain.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.dorybrain.shared.settings.ThemeMode
import com.dorybrain.shared.ui.theme.DoryBrainTheme
import com.dorybrain.shared.ui.theme.isDarkTheme

/**
 * The shared theme plus the Android-only bit: matching the system bar icons
 * to whichever scheme ended up active.
 */
@Composable
fun DoryBrainAndroidTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = isDarkTheme(themeMode)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    DoryBrainTheme(themeMode = themeMode, content = content)
}
