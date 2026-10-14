package com.pinakes.app.network

import com.pinakes.app.data.network.AuthInterceptor
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every API call carries the app language so the server answers labels and errors in it. */
class AcceptLanguageTest {
    private val request = Request.Builder().url("https://library.example/api/v1/health").build()

    @Test fun addsTheAppLanguage() {
        assertEquals("it-IT", AuthInterceptor.withLanguage(request, "it-IT").header("Accept-Language"))
    }

    @Test fun keepsAnExplicitHeader() {
        val explicit = request.newBuilder().header("Accept-Language", "de").build()
        assertEquals("de", AuthInterceptor.withLanguage(explicit, "it-IT").header("Accept-Language"))
    }

    @Test fun sendsNothingForAnUndefinedLanguage() {
        assertNull(AuthInterceptor.withLanguage(request, "und").header("Accept-Language"))
        assertNull(AuthInterceptor.withLanguage(request, "").header("Accept-Language"))
    }
}
