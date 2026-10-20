package com.pinakes.app.staff

import com.google.zxing.PlanarYUVLuminanceSource
import com.pinakes.app.ui.screens.staff.scan.BarcodeAnalyzer
import java.io.DataInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A periodical issue's barcode is the masthead EAN-13 (977…) plus a 2-digit add-on, the issue
 * number. The periodicals desk must keep the add-on from the camera or `scan-lookup` can only
 * match the masthead; the book scanner must drop it (on an ISBN the 5-digit add-on is a price).
 */
class EanAddOnDecodeTest {

    /** The label as a camera hands it over: a luminance (Y) plane, `width` and `height` first. */
    private fun source(): PlanarYUVLuminanceSource {
        val input = DataInputStream(requireNotNull(javaClass.getResourceAsStream("/barcodes/periodical-issue-addon.y")))
        val width = input.readInt()
        val height = input.readInt()
        val y = ByteArray(width * height).also(input::readFully)
        return PlanarYUVLuminanceSource(y, width, height, 0, 0, width, height, false)
    }

    @Test fun periodicalDeskKeepsTheIssueAddOn() {
        assertEquals("977123456700305", BarcodeAnalyzer.decode(source(), BarcodeAnalyzer.ISBN_FORMATS, appendEanAddOn = true))
    }

    @Test fun bookScannerReadsOnlyTheThirteenDigits() {
        assertEquals("9771234567003", BarcodeAnalyzer.decode(source(), BarcodeAnalyzer.ISBN_FORMATS, appendEanAddOn = false))
    }
}
