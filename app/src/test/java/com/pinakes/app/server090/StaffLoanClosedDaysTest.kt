package com.pinakes.app.server090

import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.ui.screens.staff.circulation.LoanCreateUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.pinakes.app.ui.screens.staff.PickItem
import org.junit.Test

/** The staff loan form refuses a start or a return on a day the library is closed. */
class StaffLoanClosedDaysTest {
    private val calendar = AvailabilityCalendar(closedDates = listOf("2030-03-09", "2030-03-10"))

    @Test fun aClosedStartOrEndIsNamed() {
        assertEquals("2030-03-09", LoanCreateUiState(calendar = calendar, start = "2030-03-09", end = "2030-03-20").closedDateChosen)
        assertEquals("2030-03-10", LoanCreateUiState(calendar = calendar, start = "2030-03-04", end = "2030-03-10").closedDateChosen)
    }

    @Test fun aLoanWithoutAStartBeginsTodaySoTodayIsChecked() {
        val today = java.time.LocalDate.now().toString()
        val closedToday = AvailabilityCalendar(closedDates = listOf(today))
        assertEquals(today, LoanCreateUiState(calendar = closedToday).closedDateChosen)
        // A reservation without a start has no implicit day.
        assertNull(LoanCreateUiState(calendar = closedToday, reservation = true).closedDateChosen)
    }

    @Test fun submitWaitsForTheCalendar() {
        val picked = LoanCreateUiState(user = PickItem(1, "Ada"), book = PickItem(2, "Emma"))
        assertTrue(picked.canSubmit)
        assertFalse(picked.copy(calendarLoading = true).canSubmit)
        // A failed fetch keeps the form usable: the server decides.
        assertTrue(picked.copy(calendarFailed = true).canSubmit)
    }

    @Test fun openDaysOrNoCalendarPass() {
        assertNull(LoanCreateUiState(calendar = calendar, start = "2030-03-04", end = "2030-03-18").closedDateChosen)
        assertNull(LoanCreateUiState(calendar = null, start = "2030-03-09").closedDateChosen)
        assertEquals(setOf("2030-03-09", "2030-03-10"), LoanCreateUiState(calendar = calendar).closedDates)
    }
}
