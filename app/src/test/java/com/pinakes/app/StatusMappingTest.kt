package com.pinakes.app

import com.pinakes.app.ui.common.StatusMapping
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which reservation statuses expose the "Cancel" action (touched area: reservation UX). */
class StatusMappingTest {

    @Test fun pendingIsCancellable() {
        assertTrue(StatusMapping.isReservationCancellable("pending"))
        assertTrue(StatusMapping.isReservationCancellable("pendente"))
        assertTrue(StatusMapping.isReservationCancellable("in_attesa"))
    }

    @Test fun prenotatoIsCancellable() {
        assertTrue(StatusMapping.isReservationCancellable("prenotato"))
    }

    @Test fun activeIsCancellable() {
        assertTrue(StatusMapping.isReservationCancellable("attiva"))
        assertTrue(StatusMapping.isReservationCancellable("active"))
    }

    @Test fun readyForPickupIsNotCancellable() {
        // Approved/ready reservations can't be self-cancelled from the app.
        assertFalse(StatusMapping.isReservationCancellable("da_ritirare"))
    }

    @Test fun cancelledIsNotCancellable() {
        assertFalse(StatusMapping.isReservationCancellable("annullata"))
        assertFalse(StatusMapping.isReservationCancellable("scaduto"))
    }

    @Test fun unknownStatusIsNotCancellable() {
        assertFalse(StatusMapping.isReservationCancellable("whatever"))
        assertFalse(StatusMapping.isReservationCancellable(""))
    }
}
