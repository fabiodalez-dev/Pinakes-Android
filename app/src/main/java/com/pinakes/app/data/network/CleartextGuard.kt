package com.pinakes.app.data.network

import com.pinakes.app.data.store.SessionStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * App-level cleartext gate.
 *
 * The Android network-security-config permits cleartext at the OS level (it can't whitelist a
 * runtime-entered instance host), so the app is the sole gatekeeper. This interceptor enforces
 * the same "HTTPS required, except loopback / opted-in" rule as [NetworkModule.isTransportAllowed]
 * for EVERY OkHttp client — not just the API one — so image loading (Coil) and the ebook PDF
 * download can't silently downgrade to plain HTTP on an otherwise-HTTPS instance.
 *
 * A plain-HTTP request to a non-loopback host is refused unless the user has explicitly enabled
 * the "Allow insecure HTTP" opt-in ([SessionStore.allowInsecureHttp]).
 */
class CleartextGuardInterceptor(private val allowInsecureHttp: () -> Boolean) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!url.isHttps && !isLoopback(url.host) && !allowInsecureHttp()) {
            throw IOException(
                "Cleartext HTTP blocked for ${url.host}. Enable \"Allow insecure HTTP\" to connect over plain HTTP.",
            )
        }
        return chain.proceed(chain.request())
    }

    private fun isLoopback(host: String): Boolean =
        host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" || host == "::1" || host == "[::1]"
}

/** Reaches the singleton [SessionStore] from code that can't be constructor-injected
 *  (the Coil [com.pinakes.app.PinakesApplication] image loader, the PDF downloader). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NetworkEntryPoint {
    fun sessionStore(): SessionStore
}
