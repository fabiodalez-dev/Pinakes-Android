package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hub → Reservations: the queue by title, cancel with a reason, and the new-reservation form. */
class ReservationsFlowTest : AppFlowTest() {

    @Test fun cancelAReservationWithAReason() {
        launch {
            json("GET staff/reservations", "staff_reservations")
            json("POST staff/reservations/31/cancel", "staff_reservation_31_cancelled")
        }
        openStaffHub()
        click(text(R.string.staff_reservations))
        waitText("Orlando")
        waitFor(androidx.compose.ui.test.hasText(text(R.string.staff_queue_position, 2), substring = true))
        clickTag("reservation-31")
        click(text(R.string.staff_reservation_cancel))
        typeInto(text(R.string.staff_reason_optional), "Requested by phone")
        clickInDialog(text(R.string.staff_reservation_cancel))
        val body = lastBody("POST", "staff/reservations/31/cancel")
        assertTrue(body, body.contains("Requested by phone"))
        waitText(text(R.string.staff_reservation_cancelled))
    }

    @Test fun newReservationPostsUserAndBookWithAKey() {
        launch {
            json("GET staff/reservations", "staff_reservations")
            json("GET staff/users", "staff_users")
            json("GET staff/books/5/availability", "staff_book_availability")
            json("POST staff/reservations", "staff_reservation_31_cancelled", status = 201)
        }
        openStaffHub()
        click(text(R.string.staff_reservations))
        clickDescription(text(R.string.staff_reservation_new))
        click(text(R.string.staff_pick_user_placeholder))
        typeIntoTag("picker-search", "Ada")
        click("Ada Rossi")
        click(text(R.string.staff_pick_book_placeholder))
        typeIntoTag("picker-search", "Orl")
        click("Orlando")
        click(text(R.string.staff_reservation_create))
        waitRequest("POST", "staff/reservations")
        val request = api.recorded("POST", "staff/reservations").last()
        val body = request.body.clone().readUtf8()
        assertTrue(body, body.contains("\"user_id\":3") && body.contains("\"book_id\":5"))
        assertTrue(request.getHeader("Idempotency-Key").orEmpty().length == 36)
        // Back on the queue.
        waitText(text(R.string.staff_reservations_active))
    }
}
