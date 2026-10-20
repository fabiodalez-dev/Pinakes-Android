package com.pinakes.app.data.staff

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Prepares a photo or picked image for `POST /staff/books/{id}/cover`: decoded with
 * sub-sampling (never the full camera resolution in memory), rotated per EXIF, scaled to at
 * most [MAX_SIDE] px and re-encoded as JPEG under the server's 2 MB limit. Re-encoding also
 * drops any metadata (location) the original file carried.
 */
// android.media.ExifInterface is reliable from API 24 with streams; minSdk is 26, so the
// AndroidX copy (an extra dependency) is not needed.
@SuppressLint("ExifInterface")
object CoverImageEncoder {
    private const val MAX_SIDE = 1600
    private const val MAX_BYTES = 1_900_000

    /** JPEG bytes, or null when the image cannot be read. Call off the main thread. */
    fun encode(context: Context, uri: Uri): ByteArray? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val longest = max(decoded.width, decoded.height)
        val scale = if (longest > MAX_SIDE) MAX_SIDE.toFloat() / longest else 1f
        val matrix = Matrix().apply {
            if (scale != 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val bitmap = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        var quality = 88
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 10
        } while (bytes.size > MAX_BYTES && quality >= 40)
        if (bitmap !== decoded) bitmap.recycle()
        decoded.recycle()
        if (bytes.size > MAX_BYTES) null else bytes
    }.getOrNull()
}
