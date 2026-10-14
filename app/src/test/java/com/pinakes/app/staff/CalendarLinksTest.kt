package com.pinakes.app.staff

import com.pinakes.app.data.calendar.CalendarLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarLinksTest {

    private val feed = "https://lib.example.org/api/v1/calendar/feed/" + "a".repeat(64) + ".ics"

    @Test fun httpsBecomesWebcal() {
        assertEquals("webcal://lib.example.org/api/v1/calendar/feed/" + "a".repeat(64) + ".ics", CalendarLinks.toWebcal(feed))
    }

    @Test fun httpLoopbackBecomesWebcalToo() {
        assertEquals("webcal://10.0.2.2:8081/x.ics", CalendarLinks.toWebcal("http://10.0.2.2:8081/x.ics"))
    }

    @Test fun schemeIsCaseInsensitiveAndOtherInputUnchanged() {
        assertEquals("webcal://h/x.ics", CalendarLinks.toWebcal("HTTPS://h/x.ics"))
        assertEquals("ftp://h/x", CalendarLinks.toWebcal("ftp://h/x"))
    }

    @Test fun webcalBackToHttps() {
        assertEquals("https://h/x.ics", CalendarLinks.toHttps("webcal://h/x.ics"))
        assertEquals("https://h/x.ics", CalendarLinks.toHttps("https://h/x.ics"))
    }

    @Test fun subscribeUrlPrefersServerWebcal() {
        assertEquals("webcal://srv/y.ics", CalendarLinks.subscribeUrl(feed, "webcal://srv/y.ics"))
        assertEquals(CalendarLinks.toWebcal(feed), CalendarLinks.subscribeUrl(feed, null))
        assertEquals(CalendarLinks.toWebcal(feed), CalendarLinks.subscribeUrl(feed, " "))
        assertNull(CalendarLinks.subscribeUrl(null, null))
    }

    @Test fun maskHidesTheTokenButKeepsHostAndTail() {
        val token = "0123456789abcdef".repeat(4)
        val masked = CalendarLinks.mask("https://lib.example.org/api/v1/calendar/feed/$token.ics")
        assertEquals("lib.example.org/…abcdef.ics", masked)
        assertEquals(false, masked.contains(token))
    }

    @Test fun maskOfHostOnlyUrl() {
        assertEquals("h", CalendarLinks.mask("https://h"))
    }
}
