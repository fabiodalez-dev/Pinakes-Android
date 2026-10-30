package com.pinakes.app.server090

import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.common.BitArray
import com.google.zxing.oned.Code128Reader
import com.pinakes.app.ui.common.CardBarcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The library card code drawn as the printed card's Code128 (Pinakes 0.9.0) reads back as the code. */
class CardBarcodeTest {
    private fun decode(modules: BooleanArray): String {
        val row = BitArray(modules.size + 2 * CardBarcode.QUIET_ZONE)
        modules.forEachIndexed { i, black -> if (black) row.set(i + CardBarcode.QUIET_ZONE) }
        val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.CODE_128))
        return Code128Reader().decodeRow(0, row, hints).text
    }

    @Test fun aCardCodeReadsBackAsItself() {
        for (code in listOf("TES000123", "000123", "LIB-2026/07", "a b")) {
            val bars = CardBarcode.modules(code)
            assertNotNull(bars)
            assertEquals(code, decode(bars!!))
        }
    }

    @Test fun surroundingSpacesAreNotEncoded() {
        assertEquals("TES1", decode(CardBarcode.modules("  TES1 ")!!))
    }

    @Test fun whatCode128CannotHoldIsNotDrawn() {
        // As on the printed card: only printable ASCII; the text line still shows the code.
        assertNull(CardBarcode.modules("Tessera-è"))
        assertNull(CardBarcode.modules("TES\u0007"))
        assertNull(CardBarcode.modules(""))
        assertNull(CardBarcode.modules(null))
        assertNull(CardBarcode.modules("X".repeat(81)))
    }
}
