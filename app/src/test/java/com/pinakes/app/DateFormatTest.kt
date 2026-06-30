package com.pinakes.app

import com.pinakes.app.ui.common.DateFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locale/zone-aware date formatting used across loans, profile and notifications.
 * Assertions avoid asserting exact localized strings (which vary by the test machine's
 * locale) and instead check the locale-independent invariants: em-dash on empty,
 * raw passthrough on parse failure, and a 4-digit year in formatted output.
 */
class DateFormatTest {

    @Test fun dateNullOrBlankIsEmDash() {
        assertEquals("—", DateFormat.date(null))
        assertEquals("—", DateFormat.date(""))
        assertEquals("—", DateFormat.date("   "))
    }

    @Test fun dateUnparseableReturnsRawInput() {
        // Robust to bad input: returns the raw string rather than throwing.
        assertEquals("not-a-date", DateFormat.date("not-a-date"))
    }

    @Test fun dateFromFullIsoTimestampIsFormatted() {
        val out = DateFormat.date("2026-06-12T14:30:00Z")
        assertNotEquals("—", out)
        assertNotEquals("2026-06-12T14:30:00Z", out)   // it reformatted, not echoed
        assertTrue("expected the year in '$out'", out.contains("2026"))
    }

    @Test fun dateFromDateOnlyStringIsFormatted() {
        val out = DateFormat.date("2026-06-12")
        assertNotEquals("—", out)
        assertTrue("expected the year in '$out'", out.contains("2026"))
    }

    @Test fun dateTimeNullIsEmDash() {
        assertEquals("—", DateFormat.dateTime(null))
        assertEquals("—", DateFormat.dateTime(""))
    }

    @Test fun dateTimeFromIsoIsFormatted() {
        val out = DateFormat.dateTime("2026-06-12T14:30:00Z")
        assertNotEquals("—", out)
        assertTrue("expected the year in '$out'", out.contains("2026"))
    }

    @Test fun dateTimeFromDateOnlyFallsBackToDate() {
        // No time component → dateTime() defers to date() rather than failing.
        val out = DateFormat.dateTime("2026-06-12")
        assertNotEquals("—", out)
        assertTrue(out.contains("2026"))
    }

    @Test fun todayIsoIsYyyyMmDd() {
        assertTrue(DateFormat.todayIso().matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }
}
