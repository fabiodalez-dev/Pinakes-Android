package com.pinakes.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matrix for [isExpectedBackendFailure], the pure core of the Sentry `beforeSend`
 * filter that drops self-hosted-backend outage noise while keeping real signal.
 */
class SentryBackendFilterTest {

    private val apiUrl = "https://lib.example.org/api/v1/books"
    private val healthUrl = "https://lib.example.org/api/v1/health"

    // ── Transient upstream 5xx on a normal endpoint → dropped ───────────────
    @Test fun drops502() = assertTrue(isExpectedBackendFailure(true, 502, apiUrl))
    @Test fun drops503() = assertTrue(isExpectedBackendFailure(true, 503, apiUrl))
    @Test fun drops504() = assertTrue(isExpectedBackendFailure(true, 504, apiUrl))

    // ── Real server / client errors on a normal endpoint → kept ─────────────
    @Test fun keeps500() = assertFalse(isExpectedBackendFailure(true, 500, apiUrl))
    @Test fun keeps400() = assertFalse(isExpectedBackendFailure(true, 400, apiUrl))
    @Test fun keeps404() = assertFalse(isExpectedBackendFailure(true, 404, apiUrl))
    @Test fun keeps401() = assertFalse(isExpectedBackendFailure(true, 401, apiUrl))

    // ── Health probe failures are expected → dropped for any status ─────────
    @Test fun dropsHealth503() = assertTrue(isExpectedBackendFailure(true, 503, healthUrl))
    @Test fun dropsHealth500() = assertTrue(isExpectedBackendFailure(true, 500, healthUrl))
    @Test fun dropsHealthWithQueryString() =
        assertTrue(isExpectedBackendFailure(true, 500, "$healthUrl?ts=123"))
    @Test fun dropsHealthWithTrailingSlash() =
        assertTrue(isExpectedBackendFailure(true, 500, "$healthUrl/"))
    @Test fun dropsHealthUnknownStatus() =
        assertTrue(isExpectedBackendFailure(true, null, healthUrl))

    // ── A non-HTTP crash is NEVER dropped, even with a 5xx-looking status ────
    @Test fun keepsNonHttpEvenWith503() =
        assertFalse(isExpectedBackendFailure(false, 503, apiUrl))
    @Test fun keepsNonHttpOnHealthUrl() =
        assertFalse(isExpectedBackendFailure(false, 503, healthUrl))

    // ── Unknowns on a normal endpoint → kept ────────────────────────────────
    @Test fun keepsUnknownStatusOnApi() =
        assertFalse(isExpectedBackendFailure(true, null, apiUrl))
    @Test fun keepsNullUrlWith500() =
        assertFalse(isExpectedBackendFailure(true, 500, null))

    // ── A transient 5xx with an unknown URL is still transient → dropped ────
    @Test fun dropsNullUrlWith503() =
        assertTrue(isExpectedBackendFailure(true, 503, null))
}
