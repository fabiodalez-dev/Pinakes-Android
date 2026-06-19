package com.pinakes.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Brand tokens — the single accent. Magenta is the ONLY colour with meaning.
// No second brand hue (no indigo/violet). See DESIGN.md.
// ---------------------------------------------------------------------------

/** Pinakes signature magenta — primary / accent. */
val BrandMagenta     = Color(0xFFD70161)
/** Deeper magenta for pressed / emphasis. */
val BrandMagentaDeep = Color(0xFFB0235A)

// ---------------------------------------------------------------------------
// Availability tints — the only extra semantic colours (clean, low-chroma).
// ---------------------------------------------------------------------------

// Available — green
val AvailableContainerLight   = Color(0xFFC7EBD1)
val AvailableOnContainerLight = Color(0xFF0B5733)
val AvailableContainerDark    = Color(0xFF14442B)
val AvailableOnContainerDark  = Color(0xFFA9E6C0)

// Limited / due-soon — amber
val DueSoonContainerLight   = Color(0xFFFBE2B3)
val DueSoonOnContainerLight = Color(0xFF6B4E00)
val DueSoonContainerDark    = Color(0xFF4A3500)
val DueSoonOnContainerDark  = Color(0xFFF0C36B)

// ---------------------------------------------------------------------------
// Light scheme (default). Cool-neutral surfaces, magenta accent. No brown.
// ---------------------------------------------------------------------------
val LightColors = lightColorScheme(
    primary = Color(0xFFD70161),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E5),
    onPrimaryContainer = Color(0xFF3E001C),

    secondary = Color(0xFF6C5C62),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E6EB),
    onSecondaryContainer = Color(0xFF25141B),

    tertiary = Color(0xFFB0235A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF3E001B),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFCFAFB),
    onBackground = Color(0xFF1C1B1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1C),
    surfaceVariant = Color(0xFFECE9EB),
    onSurfaceVariant = Color(0xFF5A585B),
    surfaceTint = Color(0xFFD70161),

    surfaceDim = Color(0xFFE0DDDF),
    surfaceBright = Color(0xFFFCFAFB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F5F7),
    surfaceContainer = Color(0xFFF3F0F2),
    surfaceContainerHigh = Color(0xFFEDEAEC),
    surfaceContainerHighest = Color(0xFFE7E4E6),

    outline = Color(0xFFC7C4C7),
    outlineVariant = Color(0xFFE6E2E5),

    inverseSurface = Color(0xFF313031),
    inverseOnSurface = Color(0xFFF3EFF1),
    inversePrimary = Color(0xFFFFB1C8),

    scrim = Color(0xFF000000),
)

// ---------------------------------------------------------------------------
// Dark scheme (opt-in). Cool-neutral, never brown.
// ---------------------------------------------------------------------------
val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF83254A),
    onPrimaryContainer = Color(0xFFFFD9E5),

    secondary = Color(0xFFD7BFC6),
    onSecondary = Color(0xFF3B2930),
    secondaryContainer = Color(0xFF534149),
    onSecondaryContainer = Color(0xFFF3E6EB),

    tertiary = Color(0xFFFFB1C5),
    onTertiary = Color(0xFF5E112B),
    tertiaryContainer = Color(0xFF7E2942),
    onTertiaryContainer = Color(0xFFFFD9E1),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF141315),
    onBackground = Color(0xFFE6E1E3),
    surface = Color(0xFF141315),
    onSurface = Color(0xFFE6E1E3),
    surfaceVariant = Color(0xFF48464A),
    onSurfaceVariant = Color(0xFFC9C5CA),
    surfaceTint = Color(0xFFFFB1C8),

    surfaceDim = Color(0xFF141315),
    surfaceBright = Color(0xFF3A383B),
    surfaceContainerLowest = Color(0xFF0F0E10),
    surfaceContainerLow = Color(0xFF1C1B1D),
    surfaceContainer = Color(0xFF201F21),
    surfaceContainerHigh = Color(0xFF2B292C),
    surfaceContainerHighest = Color(0xFF363437),

    outline = Color(0xFF928F94),
    outlineVariant = Color(0xFF48464A),

    inverseSurface = Color(0xFFE6E1E3),
    inverseOnSurface = Color(0xFF313031),
    inversePrimary = Color(0xFFD70161),

    scrim = Color(0xFF000000),
)
