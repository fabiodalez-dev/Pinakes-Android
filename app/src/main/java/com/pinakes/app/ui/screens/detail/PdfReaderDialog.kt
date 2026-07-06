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
import kotlinx.coroutines.withContext
import android.content.Context
import com.pinakes.app.data.network.CleartextGuardInterceptor
import com.pinakes.app.data.network.NetworkEntryPoint
import dagger.hilt.android.EntryPointAccessors
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private sealed interface PdfState {
    data object Loading : PdfState
    data class Ready(val pageCount: Int, val renderer: PdfRenderer, val pfd: ParcelFileDescriptor) : PdfState
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
        state = withContext(Dispatchers.IO) {
            runCatching {
                val file = downloadToCache(pdfUrl, context.cacheDir, guardedHttpClient(context))
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                PdfState.Ready(renderer.pageCount, renderer, pfd) as PdfState
            }.getOrElse { PdfState.Error }
        }
    }

    // Hold the resources to release on dispose.
    val current = state
    androidx.compose.runtime.DisposableEffect(current) {
        onDispose {
            if (current is PdfState.Ready) {
                runCatching { current.renderer.close() }
                runCatching { current.pfd.close() }
            }
        }
    }

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
                    items((0 until s.pageCount).toList()) { index ->
                        PdfPage(renderer = s.renderer, index = index, pageCount = s.pageCount)
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
private fun PdfPage(renderer: PdfRenderer, index: Int, pageCount: Int) {
    val context = LocalContext.current
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(index) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                // PdfRenderer is single-threaded: serialize page access.
                synchronized(renderer) {
                    val page = renderer.openPage(index)
                    val targetWidth = context.resources.displayMetrics.widthPixels
                    val scale = targetWidth.toFloat() / page.width.toFloat()
                    val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AndroidColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                }
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
                contentDescription = stringResource(R.string.ebook_page_of, index + 1, pageCount),
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
    return OkHttpClient.Builder()
        .addInterceptor(CleartextGuardInterceptor { session.allowInsecureHttp })
        .build()
}

/** Streams [url] into a temp file under [cacheDir] and returns it. Throws on a non-2xx response. */
private fun downloadToCache(url: String, cacheDir: File, client: OkHttpClient): File {
    val out = File.createTempFile("pinakes-ebook-", ".pdf", cacheDir)
    val request = Request.Builder().url(url).build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            out.delete()
            error("HTTP ${response.code}")
        }
        val body = response.body ?: run { out.delete(); error("Empty body") }
        out.outputStream().use { sink -> body.byteStream().copyTo(sink) }
    }
    return out
}
