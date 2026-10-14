package com.pinakes.app.data.repository

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream

/**
 * A request body streamed from [open] (a picked document): large files (audiobooks, archive
 * scans, videos) are never held in memory.
 *
 * [writeTo] runs on OkHttp's dispatcher thread, where anything but an [IOException] is rethrown
 * and would crash the app (it never reaches `apiCall`). A provider that refuses the stream
 * (`SecurityException`, a null stream, a revoked grant) is therefore turned into an
 * [IOException], which the call reports as an ordinary failure.
 */
class StreamingBody(
    private val type: MediaType,
    private val length: Long,
    private val open: () -> InputStream,
) : RequestBody() {
    override fun contentType(): MediaType = type
    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        val stream = try {
            open()
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("The document could not be read", e)
        }
        try {
            stream.use { input -> sink.writeAll(input.source()) }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("The document could not be read", e)
        }
    }
}
