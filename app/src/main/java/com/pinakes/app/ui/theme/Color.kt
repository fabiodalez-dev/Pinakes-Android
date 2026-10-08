package com.pinakes.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** Server-ready palette. Discovery does not expose theme settings yet. */
data class ThemePalette(
    val primary: Color = Color(0xFFD70161),
    val secondary: Color = Color(0xFF111827),
    val button: Color = Color(0xFFD70262),
    val buttonText: Color = Color.White,
    val cardStyle: CardStyle = CardStyle.Classic,
    val heroStyle: HeroStyle = HeroStyle.Covers,
)

enum class CardStyle { Classic, Tinted }
enum class HeroStyle { Covers, Centered }

/** CSS color-mix(in srgb): [share] is the proportion of [a]. */
fun mix(a: Color, b: Color, share: Float): Color {
    val t = share.coerceIn(0f, 1f)
    return Color(a.red * t + b.red * (1f - t), a.green * t + b.green * (1f - t),
        a.blue * t + b.blue * (1f - t), a.alpha * t + b.alpha * (1f - t))
}

fun contrastRatio(a: Color, b: Color): Double {
    fun luminance(c: Color): Double {
        fun linear(v: Float): Double = if (v <= .04045f) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
        return .2126 * linear(c.red) + .7152 * linear(c.green) + .0722 * linear(c.blue)
    }
    val x = luminance(a); val y = luminance(b)
    return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
}

/** Move a theme colour only as far as needed, as on the current web frontend. */
fun readableColor(color: Color, background: Color, minimum: Double = 4.5): Color {
    if (contrastRatio(color, background) >= minimum) return color
    val target = if (contrastRatio(Color.Black, background) > contrastRatio(Color.White, background)) Color.Black else Color.White
    var low = 0f; var high = 1f
    repeat(20) {
        val amount = (low + high) / 2f
        if (contrastRatio(mix(target, color, amount), background) >= minimum) high = amount else low = amount
    }
    return mix(target, color, high)
}

class PinakesColors(palette: ThemePalette, val darkMode: Boolean) {
    val accent = palette.primary
    val background = if (darkMode) Color(0xFF141315) else Color(0xFFFBFAF9)
    val surface = if (darkMode) Color(0xFF201F21) else Color.White
    val ink = if (darkMode) Color(0xFFE6E1E3) else Color(0xFF1B1720)
    val muted = if (darkMode) Color(0xFFC9C5CA) else Color(0xFF6B6470)
    val soft = if (darkMode) Color(0xFF2B292C) else Color(0xFFF1EDEF)
    val line = if (darkMode) Color(0xFF48464A) else Color(0xFFECE8EA)
    val line2 = if (darkMode) Color(0xFF928F94) else Color(0xFFE2DCE0)
    val accentText = readableColor(if (darkMode) mix(accent, Color.White, .55f) else accent, background)
    val accentSoft = mix(accent, surface, if (darkMode) .18f else .09f)
    val accentStrong = readableColor(mix(accentText, if (darkMode) Color.White else Color.Black, .78f), accentSoft)
    val accentSofter = mix(accent, background, .05f)
    val accentLine = mix(accent, surface, .16f)
    val heroWash = if (darkMode) mix(accent, background, .10f) else mix(accent, Color(0xFFF7F1F3), .06f)
    val dark = readableColor(palette.secondary, Color.White)
    val buttonText = palette.buttonText
    val button = readableColor(palette.button, buttonText)
    val coverBlank = Color(0xFF2A2230)
    val cardStyle = palette.cardStyle
    val heroStyle = palette.heroStyle
}

val LocalPinakesColors = staticCompositionLocalOf { PinakesColors(ThemePalette(), false) }

fun lightColors(palette: ThemePalette) = PinakesColors(palette, false).let { c ->
    lightColorScheme(
        primary = c.accentText, onPrimary = Color.White,
        primaryContainer = c.accentSoft, onPrimaryContainer = c.accentStrong,
        secondary = c.dark, onSecondary = Color.White,
        secondaryContainer = c.soft, onSecondaryContainer = c.ink,
        tertiary = c.accentText, onTertiary = Color.White,
        tertiaryContainer = c.accentSoft, onTertiaryContainer = c.accentStrong,
        background = c.background, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink, onSurfaceVariant = c.muted,
        surfaceVariant = c.soft, surfaceTint = Color.Transparent,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface,
        surfaceContainer = c.soft, surfaceContainerHigh = Color(0xFFF4F0F2), surfaceContainerHighest = c.soft,
        surfaceDim = c.soft, surfaceBright = c.background,
        outline = c.line2, outlineVariant = c.line,
        inverseSurface = c.dark, inverseOnSurface = Color.White,
        inversePrimary = mix(c.accent, Color.White, .55f),
        error = Color(0xFFB42318), onError = Color.White,
        errorContainer = Color(0xFFFDECEC), onErrorContainer = Color(0xFF8A1C13),
    )
}

fun darkColors(palette: ThemePalette) = PinakesColors(palette, true).let { c ->
    darkColorScheme(
        primary = c.accentText, onPrimary = Color(0xFF141315),
        primaryContainer = c.accentSoft, onPrimaryContainer = c.accentStrong,
        secondary = c.muted, onSecondary = c.background,
        secondaryContainer = c.soft, onSecondaryContainer = c.ink,
        tertiary = c.accentText, onTertiary = c.background,
        tertiaryContainer = c.accentSoft, onTertiaryContainer = c.accentStrong,
        background = c.background, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink, onSurfaceVariant = c.muted,
        surfaceVariant = c.soft, surfaceTint = Color.Transparent,
        surfaceContainerLowest = c.background, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.soft, surfaceContainerHighest = Color(0xFF363437),
        outline = c.line2, outlineVariant = c.line,
    )
}

val LightColors = lightColors(ThemePalette())
val DarkColors = darkColors(ThemePalette())

// Calendar semantics retain their own paired colours in both app modes.
val AvailableContainerLight = Color(0xFFDCFCE7)
val AvailableOnContainerLight = Color(0xFF0B5733)
val AvailableContainerDark = Color(0xFF14442B)
val AvailableOnContainerDark = Color(0xFFA9E6C0)
val DueSoonContainerLight = Color(0xFFFBE2B3)
val DueSoonOnContainerLight = Color(0xFF6B4E00)
val DueSoonContainerDark = Color(0xFF4A3500)
val DueSoonOnContainerDark = Color(0xFFF0C36B)
