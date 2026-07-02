package com.pinakes.app

import com.pinakes.app.ui.common.DateFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** More cases for the offset-aware date formatting (touched area: loans/profile dates). */
class DateFormatMoreTest {

    @Test fun dateFromOffsetTimestampIsFormatted() {
        val out = DateFormat.date("2026-06-12T23:30:00+02:00")
        assertNotEquals("—", out)
        assertTrue("expected year in '$out'", out.contains("2026"))
    }

    @Test fun dateTimeUnparseableReturnsRawInput() {
        assertEquals("garbage", DateFormat.dateTime("garbage"))
    }

    @Test fun todayIsoParsesBackToALocalDate() {
        // todayIso() must be a valid yyyy-MM-dd that date() can format (no em-dash, no echo).
        val today = DateFormat.todayIso()
        assertTrue(today.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
        assertNotEquals("—", DateFormat.date(today))
    }
}
