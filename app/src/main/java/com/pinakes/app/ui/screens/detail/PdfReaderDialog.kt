package com.pinakes.app.ui.screens.detail

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pinakes.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import android.content.Context
import com.pinakes.app.data.network.guardedMediaHttpClient
import com.pinakes.app.data.network.NetworkEntryPoint
import dagger.hilt.android.EntryPointAccessors
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.File
import java.io.IOException
import kotlin.coroutines.resumeWithException

private sealed interface PdfState {
    data object Loading : PdfState
    data class Ready(val document: CachedPdfDocument) : PdfState
    data object Error : PdfState
}

/**
 * In-app PDF reader rendered as a full-screen [Dialog]. Downloads [pdfUrl] to the app cache with
 * OkHttp (no external PDF dependency), then renders each page to a [Bitmap] via the platform
 * [PdfRenderer] and shows them in a vertically-scrolling [LazyColumn]. Pages render lazily and on
 * the IO dispatcher; the renderer/descriptor/temp file are cleaned up on dismiss.
 */
@Composable
fun PdfReaderDialog(
    pdfUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PdfState>(PdfState.Loading) }

    LaunchedEffect(pdfUrl) {
        state = PdfState.Loading
        try {
            holdPdfDocument(pdfUrl, context.cacheDir, guardedHttpClient(context)) {
                state = PdfState.Ready(it)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state = PdfState.Error
        }
    }
    PdfReaderContent(state, onDismiss)
}

/**
 * The same reader for a PDF already on disk (a staff export downloaded with the bearer token:
 * receipts and labels never go through the unauthenticated media client). The reader works on
 * a private copy, so [file] stays available for sharing after the dialog closes.
 */
@Composable
fun PdfReaderDialog(
    file: File,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PdfState>(PdfState.Loading) }

    LaunchedEffect(file) {
        state = PdfState.Loading
        var document: CachedPdfDocument? = null
        try {
            withContext(Dispatchers.IO) {
                val copy = File.createTempFile("pinakes-export-", ".pdf", context.cacheDir)
                file.copyTo(copy, overwrite = true)
                document = CachedPdfDocument.open(copy)
            }
            state = PdfState.Ready(checkNotNull(document))
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state = PdfState.Error
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { document?.close() }
        }
    }
    PdfReaderContent(state, onDismiss)
}

@Composable
private fun PdfReaderContent(state: PdfState, onDismiss: () -> Unit) {

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        ) {
            when (val s = state) {
                is PdfState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = stringResource(R.string.ebook_loading),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 56.dp),
                    )
                }
                is PdfState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.ebook_error),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is PdfState.Ready -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items((0 until s.document.pageCount).toList()) { index ->
                        PdfPage(document = s.document, index = index)
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cover_viewer_close),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun PdfPage(document: CachedPdfDocument, index: Int) {
    val context = LocalContext.current
    var bitmap by remember(document, index) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(document, index) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                document.renderPage(index, context.resources.displayMetrics.widthPixels)
            }.getOrNull()
        }
    }

    val bmp = bitmap
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = stringResource(R.string.ebook_page_of, index + 1, document.pageCount),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.padding(40.dp))
        }
    }
}

/**
 * OkHttp client for the PDF download, carrying the same cleartext gate as the API client so an
 * ebook served over plain HTTP can't downgrade the connection on an HTTPS instance (allowed only
 * for loopback or when the user opted into insecure HTTP). Reaches [SessionStore] via the Hilt
 * EntryPoint since this download path isn't constructor-injected.
 */
private fun guardedHttpClient(context: Context): OkHttpClient {
    val session = EntryPointAccessors
        .fromApplication(context.applicationContext, NetworkEntryPoint::class.java)
        .sessionStore()
    return guardedMediaHttpClient { session.allowInsecureHttp }
}

/** Owns the renderer and file until the dialog's effect is cancelled, including during setup. */
internal suspend fun holdPdfDocument(url: String, cacheDir: File, client: OkHttpClient, onReady: (CachedPdfDocument) -> Unit): Nothing {
    var document: CachedPdfDocument? = null
    try {
        withContext(Dispatchers.IO) {
            document = CachedPdfDocument.open(downloadToCache(url, cacheDir, client))
        }
        onReady(checkNotNull(document))
        awaitCancellation()
    } finally {
        withContext(NonCancellable + Dispatchers.IO) { document?.close() }
    }
}

/** Page rendering and close share one lock: dismissal cannot close an active page. */
internal class CachedPdfDocument private constructor(
    private val file: File,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : AutoCloseable {
    val pageCount: Int = renderer.pageCount
    private var closed = false

    @Synchronized fun renderPage(index: Int, targetWidth: Int): Bitmap {
        check(!closed) { "PDF is closed" }
        return renderer.openPage(index).use { page ->
            val scale = targetWidth.toFloat() / page.width.toFloat()
            val bitmap = Bitmap.createBitmap(targetWidth, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (failure: Throwable) {
                bitmap.recycle()
                throw failure
            }
        }
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        try { renderer.close() } finally {
            try { descriptor.close() } finally { file.delete() }
        }
    }

    companion object {
        fun open(file: File): CachedPdfDocument {
            var descriptor: ParcelFileDescriptor? = null
            try {
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                return CachedPdfDocument(file, descriptor, PdfRenderer(descriptor))
            } catch (failure: Throwable) {
                try { descriptor?.close() } finally { file.delete() }
                throw failure
            }
        }
    }
}

/** Cancel the HTTP call and remove partial files; undelivered results are removed too. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal suspend fun downloadToCache(url: String, cacheDir: File, client: OkHttpClient): File = suspendCancellableCoroutine { continuation ->
    val request = Request.Builder().url(url).build()
    val call = client.newCall(request)
    val out = File.createTempFile("pinakes-ebook-", ".pdf", cacheDir)
    continuation.invokeOnCancellation { call.cancel(); out.delete() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            out.delete()
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                response.use {
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body ?: error("Empty body")
                    out.outputStream().use { sink ->
                        body.byteStream().use { source ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                if (!continuation.isActive) throw CancellationException()
                                val count = source.read(buffer)
                                if (count < 0) break
                                sink.write(buffer, 0, count)
                            }
                        }
                    }
                }
                continuation.resume(out, onCancellation = { out.delete() })
            } catch (failure: Throwable) {
                out.delete()
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
    })
}
