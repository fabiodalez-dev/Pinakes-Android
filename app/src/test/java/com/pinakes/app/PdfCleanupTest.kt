package com.pinakes.app

import android.app.Application
import com.pinakes.app.ui.screens.detail.downloadToCache
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.CountDownLatch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfCleanupTest {
    @Test fun transportFailureRemovesThePartialFile() = runBlocking {
        val dir = Files.createTempDirectory("pdf-failure-").toFile()
        try {
            val client = OkHttpClient.Builder().addInterceptor { throw IOException("disconnected") }.build()
            assertTrue(runCatching { downloadToCache("https://library.invalid/book.pdf", dir, client) }.exceptionOrNull() is IOException)
            assertEquals(0, dir.listFiles()!!.size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun httpFailureRemovesTheFile() = runBlocking {
        val dir = Files.createTempDirectory("pdf-http-").toFile()
        try { MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            assertTrue(runCatching { downloadToCache(server.url("/missing.pdf").toString(), dir, OkHttpClient()) }.isFailure)
            assertEquals(0, dir.listFiles()!!.size)
        } } finally { dir.deleteRecursively() }
    }

    @Test fun cancellingDuringDownloadRemovesThePartialFile() = runBlocking {
        val dir = Files.createTempDirectory("pdf-cancel-").toFile()
        try { MockWebServer().use { server ->
            val release = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    release.await(5, TimeUnit.SECONDS)
                    return MockResponse().setBody("download")
                }
            }
            val loading = async(Dispatchers.IO) { downloadToCache(server.url("/book.pdf").toString(), dir, OkHttpClient()) }
            try {
                assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) })
                loading.cancelAndJoin()
                assertEquals(0, dir.listFiles()!!.size)
            } finally { release.countDown() }
        } } finally { dir.deleteRecursively() }
    }
}
