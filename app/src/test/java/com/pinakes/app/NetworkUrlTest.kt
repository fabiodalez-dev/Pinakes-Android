package com.pinakes.app

import com.pinakes.app.data.network.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure URL-derivation helpers used by onboarding (touched area: networking). */
class NetworkUrlTest {

    @Test fun addsHttpsAndApiV1() {
        assertEquals("https://lib.example.org/api/v1/", NetworkModule.deriveApiBaseUrl("lib.example.org"))
    }

    @Test fun keepsExplicitHttpScheme() {
        assertEquals("http://10.0.2.2:8081/api/v1/", NetworkModule.deriveApiBaseUrl("http://10.0.2.2:8081"))
    }

    @Test fun stripsTrailingSlash() {
        assertEquals("https://lib.example.org/api/v1/", NetworkModule.deriveApiBaseUrl("https://lib.example.org/"))
    }

    @Test fun stripsUserSuppliedApiV1Suffix() {
        assertEquals("https://lib.example.org/api/v1/", NetworkModule.deriveApiBaseUrl("https://lib.example.org/api/v1"))
    }

    @Test fun stripsUserSuppliedApiSuffix() {
        assertEquals("https://lib.example.org/api/v1/", NetworkModule.deriveApiBaseUrl("https://lib.example.org/api"))
    }

    @Test fun blankStaysBlank() {
        assertEquals("", NetworkModule.deriveApiBaseUrl("   "))
    }

    @Test fun originDropsApiV1() {
        assertEquals("https://lib.example.org", NetworkModule.deriveOrigin("lib.example.org/api/v1"))
    }

    @Test fun httpsAlwaysAllowed() {
        assertTrue(NetworkModule.isTransportAllowed("https://lib.example.org/api/v1/"))
    }

    @Test fun httpAllowedOnlyForLoopback() {
        assertTrue(NetworkModule.isTransportAllowed("http://10.0.2.2:8081/api/v1/"))
        assertTrue(NetworkModule.isTransportAllowed("http://localhost/api/v1/"))
        assertTrue(NetworkModule.isTransportAllowed("http://127.0.0.1/api/v1/"))
        assertFalse(NetworkModule.isTransportAllowed("http://lib.example.org/api/v1/"))
    }

    // --- Insecure-HTTP opt-in (issue #16: HTTP-only self-hosted instances) ---

    @Test fun allowInsecurePrependsHttpForBareHost() {
        assertEquals(
            "http://lib.example.org/api/v1/",
            NetworkModule.deriveApiBaseUrl("lib.example.org", allowInsecure = true),
        )
    }

    @Test fun allowInsecureKeepsExplicitHttpsScheme() {
        // An explicit scheme always wins over the opt-in default.
        assertEquals(
            "https://lib.example.org/api/v1/",
            NetworkModule.deriveApiBaseUrl("https://lib.example.org", allowInsecure = true),
        )
    }

    @Test fun allowInsecureOriginKeepsHttp() {
        assertEquals("http://lib.example.org", NetworkModule.deriveOrigin("lib.example.org", allowInsecure = true))
    }

    @Test fun allowInsecurePermitsAnyHttpHost() {
        assertTrue(NetworkModule.isTransportAllowed("http://lib.example.org/api/v1/", allowInsecure = true))
    }

    @Test fun defaultStillRejectsRemoteHttp() {
        // Regression guard: without the opt-in, a remote HTTP host stays blocked.
        assertFalse(NetworkModule.isTransportAllowed("http://lib.example.org/api/v1/", allowInsecure = false))
    }
}
