package com.pinakes.app.server090

import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.AvailabilityDay
import com.pinakes.app.ui.screens.detail.CalendarModel
import com.pinakes.app.ui.screens.detail.DayKind
import com.pinakes.app.ui.screens.detail.firstRequestableDay
import com.pinakes.app.ui.screens.detail.isClosed
import com.pinakes.app.ui.screens.detail.loanWindowFrom
import com.pinakes.app.ui.screens.detail.openDayFrom
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Closures calendar (Mobile API 1.9.0 `closed_dates`): a closed day is never a start, gets
 * its own colour, and a proposed return date always lands on an open day.
 */
class ClosedDatesCalendarTest {
    private val today = LocalDate.parse("2030-03-01")

    private fun freeDays(from: String, count: Int) = (0 until count).map {
        AvailabilityDay(date = LocalDate.parse(from).plusDays(it.toLong()).toString(), available = 1, state = "free")
    }

    private fun calendar(closed: List<String>, maxDuration: Int? = 90, full: List<String> = emptyList()) = AvailabilityCalendar(
        totalCopies = 1,
        maxLoanDurationDays = maxDuration,
        closedDates = closed,
        days = freeDays("2030-03-01", 90).map { d -> if (d.date in full) d.copy(available = 0, state = "full") else d },
    )

    @Test fun aClosedFutureDayHasItsOwnKindAndCannotBePicked() {
        val model = CalendarModel(calendar(listOf("2030-03-05")))
        val day = LocalDate.parse("2030-03-05")
        assertEquals(DayKind.Closed, model.kind(day, today))
        assertFalse(model.isSelectable(day, today))
        assertTrue(model.isSelectable(LocalDate.parse("2030-03-06"), today))
    }

    @Test fun aClosedDayInThePastIsJustPast() {
        val model = CalendarModel(calendar(listOf("2030-02-27")))
        assertEquals(DayKind.PastOrUnknown, model.kind(LocalDate.parse("2030-02-27"), today))
    }

    @Test fun closedWinsOverFullyBookedForTheColour() {
        val model = CalendarModel(calendar(listOf("2030-03-05"), full = listOf("2030-03-05")))
        assertEquals(DayKind.Closed, model.kind(LocalDate.parse("2030-03-05"), today))
    }

    @Test fun theAvailabilityPayloadReadsTheNewFieldsAndDefaultsOnOlderServers() {
        val json = Json { ignoreUnknownKeys = true }
        val new = json.decodeFromString<AvailabilityCalendar>(
            """{"total_copies":1,"closed_dates":["2030-03-05","2030-03-12"],"loanable":false,"reservations_enabled":false}""",
        )
        assertEquals(listOf("2030-03-05", "2030-03-12"), new.closedDates)
        assertFalse(new.loanable)
        assertFalse(new.reservationsEnabled)
        val old = json.decodeFromString<AvailabilityCalendar>("""{"total_copies":1}""")
        assertTrue(old.closedDates.isEmpty())
        assertTrue(old.loanable)
        assertTrue(old.reservationsEnabled)
    }

    @Test fun openDayFromWalksOverARunOfClosedDays() {
        val cal = calendar(listOf("2030-03-09", "2030-03-10"))
        assertEquals(LocalDate.parse("2030-03-11"), cal.openDayFrom(LocalDate.parse("2030-03-09"), forward = true))
        assertEquals(LocalDate.parse("2030-03-08"), cal.openDayFrom(LocalDate.parse("2030-03-10"), forward = false))
        assertNull(cal.openDayFrom(LocalDate.parse("2030-03-10"), forward = false, notBefore = LocalDate.parse("2030-03-09")))
        assertTrue(cal.isClosed("2030-03-09"))
    }

    @Test fun thePreselectedStartSkipsClosedAndBookedDays() {
        val cal = calendar(listOf("2030-03-02", "2030-03-03"), full = listOf("2030-03-04"))
        assertEquals("2030-03-05", cal.firstRequestableDay("2030-03-02"))
        // Nothing closed: the server's earliest day is kept as it is.
        assertEquals("2030-03-02", calendar(emptyList()).firstRequestableDay("2030-03-02"))
        assertNull(cal.firstRequestableDay(null))
    }

    @Test fun aDefaultReturnOnAClosedDayMovesToTheNextOpenDay() {
        // 2030-03-04 + 14 = 2030-03-18 (closed), 2030-03-19 closed too → 2030-03-20.
        val window = loanWindowFrom("2030-03-04", calendar(listOf("2030-03-18", "2030-03-19")), 14)
        assertEquals("2030-03-20", window?.endDate)
        assertEquals(16, window?.maxDays)
        assertFalse(window!!.requiresEndDate)
        assertFalse(window.capped)
    }

    @Test fun anOlderServerWithoutTheMaximumKeepsTheDefaultReturn() {
        // closed_dates only comes with Mobile API 1.9.0, which always sends the maximum.
        val window = loanWindowFrom("2030-03-04", calendar(listOf("2030-03-18"), maxDuration = null), 14)
        assertEquals("2030-03-18", window?.endDate)
        assertFalse(window!!.requiresEndDate)
    }

    @Test fun aReturnMovedOverAClosureNeverLandsOnABookedDay() {
        // Default return 2030-03-18 is closed and 2030-03-19 is fully booked: the loan stops on
        // the last open day before the booking, and the app sends it as end_date.
        val window = loanWindowFrom("2030-03-04", calendar(listOf("2030-03-18"), full = listOf("2030-03-19")), 14)
        assertEquals("2030-03-17", window?.endDate)
        assertEquals("2030-03-19", window?.firstBlocked)
        assertTrue(window!!.capped)
        assertTrue(window.requiresEndDate)
        assertEquals(13, window.maxDays)
    }

    @Test fun aClosedAndBookedDefaultReturnCapsTheLoanBeforeIt() {
        val window = loanWindowFrom("2030-03-04", calendar(listOf("2030-03-18", "2030-03-17"), full = listOf("2030-03-18")), 14)
        assertEquals("2030-03-16", window?.endDate)
        assertEquals("2030-03-18", window?.firstBlocked)
        assertTrue(window!!.requiresEndDate)
    }

    @Test fun aShortenedReturnOnAClosedDayMovesBackNotPastTheBookedDay() {
        // Booked from 2030-03-10, so the return would be 2030-03-09, which is closed: 2030-03-08.
        val window = loanWindowFrom("2030-03-04", calendar(listOf("2030-03-09"), full = listOf("2030-03-10")), 14)
        assertEquals("2030-03-08", window?.endDate)
        assertEquals("2030-03-10", window?.firstBlocked)
        assertEquals(4, window?.maxDays)
        assertTrue(window!!.requiresEndDate)
        assertTrue(window.possible)
    }

    @Test fun noOpenDayBeforeTheBookedOneMeansNoLoanFromThatStart() {
        val window = loanWindowFrom("2030-03-04", calendar(listOf("2030-03-05", "2030-03-06"), full = listOf("2030-03-07")), 14)
        assertFalse(window!!.possible)
    }

    @Test fun withoutClosuresTheWindowIsUnchanged() {
        val window = loanWindowFrom("2030-03-04", calendar(emptyList()), 14)
        assertEquals("2030-03-18", window?.endDate)
        assertEquals(14, window?.maxDays)
    }
}
