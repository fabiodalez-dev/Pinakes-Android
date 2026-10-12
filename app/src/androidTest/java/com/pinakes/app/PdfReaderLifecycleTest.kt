package com.pinakes.app

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.platform.app.InstrumentationRegistry
import com.pinakes.app.ui.screens.detail.CachedPdfDocument
import com.pinakes.app.ui.screens.detail.holdPdfDocument
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files

/** Platform PDF parsing/rendering is tested on Android, not Robolectric's stub PdfRenderer. */
class PdfReaderLifecycleTest {
    private fun cacheDirectory() = Files.createTempDirectory(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath(), "pdf-reader-").toFile()

    private fun pdfBytes(): ByteArray = ByteArrayOutputStream().use { sink ->
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(200, 300, 1).create())
            page.canvas.drawText("Pinakes PDF lifecycle", 10f, 30f, Paint().apply { textSize = 12f })
            pdf.finishPage(page)
            pdf.writeTo(sink)
        } finally { pdf.close() }
        sink.toByteArray()
    }

    @Test fun malformedPdfRemovesTheDownloadedFile() {
        val dir = cacheDirectory()
        try {
            val file = dir.resolve("invalid.pdf").apply { writeText("not a PDF") }
            assertTrue(runCatching { CachedPdfDocument.open(file) }.isFailure)
            assertFalse(file.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun closeRemovesThePdfAndIsRepeatable() {
        val dir = cacheDirectory()
        try {
            val file = dir.resolve("book.pdf").apply { writeBytes(pdfBytes()) }
            val document = CachedPdfDocument.open(file)
            assertEquals(1, document.pageCount)
            document.renderPage(0, 100).recycle()
            document.close()
            document.close()
            assertFalse(file.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun dismissingTheReaderReleasesItsFile() = runBlocking {
        val dir = cacheDirectory()
        try { MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(okio.Buffer().write(pdfBytes())))
            val ready = CompletableDeferred<Unit>()
            val reader = launch { holdPdfDocument(server.url("/book.pdf").toString(), dir, OkHttpClient()) { ready.complete(Unit) } }
            withTimeout(10_000) { ready.await() }
            assertEquals(1, dir.listFiles()!!.size)
            reader.cancelAndJoin()
            assertEquals(0, dir.listFiles()!!.size)
        } } finally { dir.deleteRecursively() }
    }
}
