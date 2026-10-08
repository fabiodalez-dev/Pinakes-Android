package com.pinakes.app

import androidx.compose.ui.graphics.Color
import com.pinakes.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class ThemePaletteTest {
    private val palettes = listOf(
        ThemePalette(),
        ThemePalette(Color(0xFF0284C7), Color(0xFF0C4A6E), Color(0xFF0EA5E9)),
        ThemePalette(Color(0xFF059669), Color(0xFF064E3B), Color(0xFF10B981)),
        ThemePalette(Color(0xFFEA580C), Color(0xFF7C2D12), Color(0xFFF97316)),
        ThemePalette(Color(0xFF1E40AF), Color(0xFF1E3A8A), Color(0xFF3B82F6)),
        ThemePalette(Color(0xFF404040), Color.Black, Color(0xFF808080)),
        ThemePalette(Color(0xFFF43F5E), Color(0xFF9F1239), Color(0xFFFB7185)),
    )

    @Test fun `theme text and filled surfaces keep AA contrast in both app modes`() {
        for (palette in palettes) for (dark in listOf(false, true)) {
            val c = PinakesColors(palette, dark)
            val pairs = listOf(c.accentText to c.background, c.accentStrong to c.accentSoft,
                c.buttonText to c.button, Color.White to c.dark, c.ink to c.surface, c.muted to c.background)
            pairs.forEach { (text, surface) -> assertTrue("$palette dark=$dark contrast=${contrastRatio(text, surface)}",
                contrastRatio(text, surface) >= 4.4999) }
        }
    }

    @Test fun `palette colour mixing uses sRGB channels like the web`() {
        val soft = mix(Color(0xFFD70161), Color.White, .09f)
        assertEquals((215f * .09f + 255f * .91f) / 255f, soft.red, (0.5f / 255f))
        assertEquals((1f * .09f + 255f * .91f) / 255f, soft.green, (0.5f / 255f))
    }

    @Test fun `a palette with dark button text preserves its pairing`() {
        val c = PinakesColors(ThemePalette(button = Color(0xFF404040), buttonText = Color.Black), false)
        assertEquals(Color.Black, c.buttonText)
        assertTrue(contrastRatio(c.buttonText, c.button) >= 4.4999)
    }
}
