package com.pinakes.app

import com.pinakes.app.data.network.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** More cases for instance-URL derivation + transport policy (touched: NetworkModule). */
class NetworkUrlMoreTest {

    @Test fun keepsExplicitPort() {
        assertEquals(
            "http://192.168.1.50:8080/api/v1/",
            NetworkModule.deriveApiBaseUrl("http://192.168.1.50:8080"),
        )
    }

    @Test fun originDropsApiV1AndTrailingSlash() {
        assertEquals("https://lib.example.org", NetworkModule.deriveOrigin("https://lib.example.org/api/v1/"))
        assertEquals("https://lib.example.org", NetworkModule.deriveOrigin("lib.example.org"))
    }

    @Test fun httpAllowedForLoopbackAndEmulatorHostOnly() {
        assertTrue(NetworkModule.isTransportAllowed("http://localhost:8081/api/v1/"))
        assertTrue(NetworkModule.isTransportAllowed("http://127.0.0.1/api/v1/"))
        assertTrue(NetworkModule.isTransportAllowed("http://10.0.2.2:8081/api/v1/"))   // Android emulator host
    }

    @Test fun plainHttpToAPublicHostIsRejected() {
        assertFalse(NetworkModule.isTransportAllowed("http://lib.example.org/api/v1/"))
    }
}
