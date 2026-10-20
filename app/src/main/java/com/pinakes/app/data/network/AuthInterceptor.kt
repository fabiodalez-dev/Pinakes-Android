package com.pinakes.app.data.network

import com.pinakes.app.data.store.SessionStore
import com.pinakes.app.ui.common.AppLocale
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Injects `Authorization: Bearer <token>` on every request, except those tagged with the
 * [PinakesApi.NO_AUTH] header (health/login/register/forgot-password). The marker header is
 * stripped before the request leaves the client.
 */
class AuthInterceptor(private val session: SessionStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = withLanguage(chain.request())

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
        val response = chain.proceed(request)
        // App-wide session expiry: a 401 on an authenticated request means the bearer
        // token is no longer valid. Clear it so SessionStore.authState flips and the nav
        // host routes every screen (Book Club included) back to login, instead of leaving
        // the user on a permanent retryable error. Public endpoints returned early above,
        // so this only fires for genuinely-authenticated calls.
        if (response.code == 401 && !token.isNullOrBlank()) {
            session.clearToken()
        }
        return response
    }

    companion object {
        private const val NO_AUTH_HEADER = "X-Pinakes-No-Auth"

        /**
         * Ask the server for labels and messages in the language the app is showing (Profile
         * language or the device one); the API falls back to its own when it does not have it.
         * A header set explicitly on the request wins.
         */
        internal fun withLanguage(request: Request, tag: String = AppLocale.current().toLanguageTag()): Request =
            if (request.header("Accept-Language") != null || tag.isBlank() || tag == "und") request
            else request.newBuilder().header("Accept-Language", tag).build()
    }
}
