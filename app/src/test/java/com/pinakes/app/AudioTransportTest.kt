@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pinakes.app

import android.app.Application
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.data.network.audioDataSourceFactory
import com.pinakes.app.data.network.guardedMediaHttpClient
import okhttp3.Dns
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudioTransportTest {
    private val loopbackDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getByName("127.0.0.1"))
    }

    @Test fun media3RejectsPublicHttpBeforeConnecting() {
        val source = audioDataSourceFactory(ApplicationProvider.getApplicationContext(), { false }).createDataSource()
        try {
            val failure = runCatching { source.open(DataSpec.Builder().setUri(Uri.parse("http://library.invalid/audio.mp3")).build()) }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(generateSequence(failure) { it.cause }.any { it.message.orEmpty().contains("Cleartext HTTP blocked") })
        } finally { source.close() }
    }

    @Test fun httpOptInAllowsMediaRequestsWithoutAnApiBearerToken() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("audio"))
            val client = guardedMediaHttpClient { true }.newBuilder().dns(loopbackDns).build()
            val url = server.url("/audio.mp3").newBuilder().host("library.invalid").build()
            client.newCall(Request.Builder().url(url).build()).execute().use { assertEquals(200, it.code) }
            assertNull(server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test fun media3CanReadLoopbackHttpWithoutOptIn() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("audio"))
            val source = audioDataSourceFactory(ApplicationProvider.getApplicationContext(), { false }).createDataSource()
            try {
                assertEquals(5L, source.open(DataSpec.Builder().setUri(server.url("/audio.mp3").toString()).build()))
                val buffer = ByteArray(5)
                assertEquals(5, source.read(buffer, 0, 5))
                assertEquals("audio", buffer.decodeToString())
            } finally { source.close() }
        }
    }

    @Test fun httpsRedirectCannotDowngradeToPublicHttpWithoutOptIn() {
        val cert = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        MockWebServer().use { secure -> MockWebServer().use { plain ->
            secure.useHttps(serverTls.sslSocketFactory(), false)
            val target = plain.url("/audio.mp3").newBuilder().host("library.invalid").build()
            plain.enqueue(MockResponse().setBody("must not be fetched"))
            secure.enqueue(MockResponse().setResponseCode(302).setHeader("Location", target))
            val client = guardedMediaHttpClient { false }.newBuilder().dns(loopbackDns)
                .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
            val failure = runCatching { client.newCall(Request.Builder().url(secure.url("/redirect")).build()).execute().close() }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(failure!!.message.orEmpty().contains("Cleartext HTTP blocked"))
            assertEquals(1, secure.requestCount)
            assertEquals(0, plain.requestCount)
        } }
    }
}
