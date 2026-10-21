package com.pinakes.app

import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.AvailabilityDay
import com.pinakes.app.ui.screens.detail.isFullyBooked
import com.pinakes.app.ui.screens.detail.loanWindowFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The loan-request sheet stops the return date the day before a fully-booked day inside the
 * library's loan duration, and says so; otherwise it proposes start + duration. Mirrors the
 * website calendar and the server's LoanWindow (Mobile API 1.8.2).
 */
class LoanWindowTest {
    private fun calendar(vararg full: String, partial: List<String> = emptyList()) = AvailabilityCalendar(
        totalCopies = 1,
        days = (full.map { AvailabilityDay(date = it, available = 0, state = "full") } +
            partial.map { AvailabilityDay(date = it, available = 1, state = "partial") }),
    )

    @Test fun aScheduledLoanInsideTheDurationCapsTheReturnDate() {
        val window = loanWindowFrom("2030-03-04", calendar("2030-03-09", "2030-03-10"), 30)
        assertEquals("2030-03-08", window?.endDate)
        assertEquals("2030-03-09", window?.firstBlocked)
        assertEquals(4, window?.maxDays)
        assertTrue(window!!.capped)
    }

    @Test fun noConflictMeansStartPlusTheDuration() {
        val window = loanWindowFrom("2030-03-04", calendar("2030-05-01"), 14)
        assertEquals("2030-03-18", window?.endDate)
        assertNull(window?.firstBlocked)
        assertEquals(14, window?.maxDays)
        assertFalse(window!!.capped)
    }

    @Test fun aConflictBeyondTheDurationDoesNotCap() {
        val window = loanWindowFrom("2030-03-04", calendar("2030-03-19"), 14)
        assertEquals("2030-03-18", window?.endDate)
        assertFalse(window!!.capped)
    }

    @Test fun thePickedDayItselfIsNeverTheConflict() {
        // The chosen start is free by construction; only the days after it are searched.
        val window = loanWindowFrom("2030-03-04", calendar("2030-03-04", "2030-03-05"), 14)
        assertEquals("2030-03-05", window?.firstBlocked)
        assertEquals("2030-03-04", window?.endDate)
        assertEquals(0, window?.maxDays)
        // A window shorter than a day is no loan: the sheet says so and disables the request.
        assertFalse(window!!.possible)
        assertTrue(loanWindowFrom("2030-03-04", calendar("2030-03-06"), 14)!!.possible)
    }

    @Test fun aFreeLabelWithNoFreeCopyIsBlockedLikeTheCalendarPaintsIt() {
        // CalendarModel paints state "free" with available 0 red and unpickable: the window agrees.
        val stale = AvailabilityCalendar(totalCopies = 1, days = listOf(AvailabilityDay(date = "2030-03-07", available = 0, state = "free")))
        val window = loanWindowFrom("2030-03-04", stale, 14)
        assertEquals("2030-03-06", window?.endDate)
        assertTrue(window!!.capped)
        assertTrue(stale.isFullyBooked("2030-03-07"))
        assertFalse(stale.isFullyBooked("2030-03-06"))
    }

    @Test fun aPartialDayStillHasAFreeCopy() {
        val window = loanWindowFrom("2030-03-04", calendar(partial = listOf("2030-03-06")), 14)
        assertFalse(window!!.capped)
    }

    @Test fun unavailableDatesCountAsBlockedEvenWithoutADayRow() {
        val window = loanWindowFrom("2030-03-04", AvailabilityCalendar(totalCopies = 1, unavailableDates = listOf("2030-03-07")), 14)
        assertEquals("2030-03-06", window?.endDate)
        assertTrue(window!!.capped)
    }

    @Test fun unknownDurationOrBadStartGivesNoWindow() {
        assertNull(loanWindowFrom("2030-03-04", calendar("2030-03-09"), null))
        assertNull(loanWindowFrom("2030-03-04", calendar("2030-03-09"), 0))
        assertNull(loanWindowFrom("not a date", calendar("2030-03-09"), 14))
        assertNull(loanWindowFrom("2030-03-04", null, null))
    }
}
