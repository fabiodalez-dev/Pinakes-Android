package com.pinakes.app.ui.screens.staff.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/**
 * CameraX analyzer that decodes EAN-13 barcodes with ZXing (pure Java, no Play services).
 *
 * Only the luminance (Y) plane is used, copied row by row to honour `rowStride`, and cropped
 * to the centre of the frame where the viewfinder guides the user. Frames alternate between
 * the sensor orientation and a 90° rotation, so a barcode is found whether the book is held
 * horizontally or vertically. Decoded text goes to [onCode] on the analysis thread; the caller
 * validates the checksum and debounces repeated reads.
 */
class BarcodeAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.EAN_13),
                DecodeHintType.TRY_HARDER to true,
            )
        )
    }
    private var frame = 0L
    private var buffer = ByteArray(0)
    private var rotated = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes.firstOrNull() ?: return
            val width = image.width
            val height = image.height
            val size = width * height
            if (buffer.size != size) buffer = ByteArray(size)
            val source = plane.buffer
            source.rewind()
            val rowStride = plane.rowStride
            if (rowStride == width) {
                source.get(buffer, 0, size.coerceAtMost(source.remaining()))
            } else {
                for (row in 0 until height) {
                    source.position(row * rowStride)
                    source.get(buffer, row * width, width.coerceAtMost(source.remaining()))
                }
            }

            val rotate = (frame++ % 2L) == 1L
            val (data, w, h) = if (rotate) {
                if (rotated.size != size) rotated = ByteArray(size)
                rotate90(buffer, rotated, width, height)
                Triple(rotated, height, width)
            } else {
                Triple(buffer, width, height)
            }
            // Centre crop: the full width, the middle 60% of the height (the scan band).
            val cropH = (h * 0.6f).toInt().coerceAtLeast(1)
            val top = (h - cropH) / 2
            val luminance = PlanarYUVLuminanceSource(data, w, h, 0, top, w, cropH, false)
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(luminance)))
            result?.text?.let(onCode)
        } catch (_: NotFoundException) {
            // No barcode in this frame: the normal case.
        } catch (_: ReaderException) {
            // Checksum/format failure on a partial read: ignore, the next frame retries.
        } catch (_: RuntimeException) {
            // A malformed frame must never crash the camera pipeline.
        } finally {
            reader.reset()
            image.close()
        }
    }

    private fun rotate90(src: ByteArray, dst: ByteArray, width: Int, height: Int) {
        // Clockwise: (x, y) → (height - 1 - y, x) in a height × width image.
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                dst[x * height + (height - 1 - y)] = src[i++]
            }
        }
    }
}
