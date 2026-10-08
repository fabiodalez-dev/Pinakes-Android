package com.pinakes.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** User-selectable theme. Default is [LIGHT] (DESIGN.md: "Light is the default theme"). */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Composable
fun PinakesTheme(
    mode: ThemeMode = ThemeMode.LIGHT,
    palette: ThemePalette = ThemePalette(),
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // With edge-to-edge the status/navigation bars are transparent, so the system
    // soft-button + status icons must follow the APP theme, not the system dark
    // mode — otherwise a light app under a dark-mode system shows white icons on a
    // light background (invisible). Light theme → dark icons; dark theme → light.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    val colors = remember(palette, dark) { PinakesColors(palette, dark) }
    CompositionLocalProvider(LocalPinakesColors provides colors) {
        MaterialTheme(
            colorScheme = remember(palette, dark) { if (dark) darkColors(palette) else lightColors(palette) },
            typography = PinakesTypography,
            shapes = PinakesShapes,
            content = content,
        )
    }
}
