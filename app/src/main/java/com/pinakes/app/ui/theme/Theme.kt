package com.pinakes.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** User-selectable theme. Default is [LIGHT] (DESIGN.md: "Light is the default theme"). */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Composable
fun PinakesTheme(
    mode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = PinakesTypography,
        shapes = PinakesShapes,
        content = content,
    )
}
