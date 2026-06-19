package com.pinakes.app.data.network

import com.pinakes.app.data.store.SessionStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Injects `Authorization: Bearer <token>` on every request, except those tagged with the
 * [PinakesApi.NO_AUTH] header (health/login/register/forgot-password). The marker header is
 * stripped before the request leaves the client.
 */
class AuthInterceptor(private val session: SessionStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Public endpoints carry the no-auth marker — drop it and send as-is.
        if (original.header(NO_AUTH_HEADER) != null) {
            val cleaned = original.newBuilder().removeHeader(NO_AUTH_HEADER).build()
            return chain.proceed(cleaned)
        }

        val token = session.token
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }

    companion object {
        private const val NO_AUTH_HEADER = "X-Pinakes-No-Auth"
    }
}
