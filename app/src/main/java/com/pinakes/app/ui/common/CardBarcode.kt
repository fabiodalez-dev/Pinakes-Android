package com.pinakes.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.zxing.oned.Code128Writer

/**
 * The library card code as a Code128 barcode, the default of the printed card (Pinakes 0.9.0,
 * Settings → Cards) and what the desk and the visitor-log kiosk scan. Pure, unit-tested.
 */
object CardBarcode {
    /** Quiet zone each side, in modules, so a scanner finds the start of the code. */
    const val QUIET_ZONE = 10

    /**
     * The bars of [code] (true = black module), or null when Code128 cannot hold it: as on the
     * printed card, only printable ASCII is encoded, and the text line still shows the code.
     */
    fun modules(code: String?): BooleanArray? {
        val value = code?.trim().orEmpty()
        if (value.isEmpty() || value.length > 80 || value.any { it.code !in 0x20..0x7E }) return null
        return runCatching { Code128Writer().encode(value) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Draws [code] as black bars on white (whatever the theme: scanners need the contrast), or
 * nothing when it cannot be encoded.
 */
@Composable
fun CardBarcodeImage(code: String, description: String, modifier: Modifier = Modifier) {
    val bars = remember(code) { CardBarcode.modules(code) } ?: return
    Canvas(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color.White)
            .padding(vertical = 8.dp)
            .semantics { contentDescription = description },
    ) {
        val total = bars.size + 2 * CardBarcode.QUIET_ZONE
        val module = size.width / total
        bars.forEachIndexed { i, black ->
            if (black) {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset((i + CardBarcode.QUIET_ZONE) * module, 0f),
                    size = Size(module, size.height),
                )
            }
        }
    }
}
