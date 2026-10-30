package com.pinakes.app.server090

import com.pinakes.app.data.model.ReservationWrite
import com.pinakes.app.ui.screens.staff.circulation.LoanCreateUiState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A staff reservation may ignore the patron type's reservation limits (Pinakes 0.9.0,
 * `override_limits` on POST /staff/reservations), as the website's "new reservation" form allows.
 */
class StaffReservationOverrideTest {
    // The network client's settings: null fields are left out of the body.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    private val form = LoanCreateUiState(reservation = true, start = "2030-03-04", end = "2030-03-20")

    @Test fun theOverrideIsSentOnlyWhenTickedAndTheRulesAreOn() {
        assertEquals(true, form.copy(limitsOverridable = true, overrideLimits = true).reservationWrite(1, 2).overrideLimits)
        assertNull(form.copy(limitsOverridable = true, overrideLimits = false).reservationWrite(1, 2).overrideLimits)
        // Rules off on the server: there are no limits to override, as on the website.
        assertNull(form.copy(limitsOverridable = false, overrideLimits = true).reservationWrite(1, 2).overrideLimits)
    }

    @Test fun theBodyKeepsTheDatesAndIdsAsBefore() {
        val body = form.reservationWrite(7, 9)
        assertEquals(ReservationWrite(userId = 7, bookId = 9, requestedFrom = "2030-03-04", requestedTo = "2030-03-20"), body)
    }

    @Test fun withoutTheOverrideTheKeyIsNotSent() {
        val plain = json.encodeToString(form.reservationWrite(7, 9))
        assertFalse(plain.contains("override_limits"))
        val forced = json.encodeToString(form.copy(limitsOverridable = true, overrideLimits = true).reservationWrite(7, 9))
        assertTrue(forced.contains("\"override_limits\":true"))
    }
}
