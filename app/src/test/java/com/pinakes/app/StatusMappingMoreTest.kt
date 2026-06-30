package com.pinakes.app

import com.pinakes.app.ui.common.StatusMapping
import com.pinakes.app.ui.components.AvailabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Colour-bucket / priority-group mapping of backend loan & reservation states. */
class StatusMappingMoreTest {

    @Test fun loanMapsKnownStatesToColourBuckets() {
        assertEquals(AvailabilityStatus.Available, StatusMapping.loan("in_corso").first)
        assertEquals(AvailabilityStatus.Overdue, StatusMapping.loan("in_ritardo").first)
        assertEquals(AvailabilityStatus.ReservedReady, StatusMapping.loan("da_ritirare").first)
        assertEquals(AvailabilityStatus.DueSoon, StatusMapping.loan("pendente").first)
    }

    @Test fun loanUnknownStateFallsBackToTitleCasedRaw() {
        val label = StatusMapping.loan("qualche_stato_strano").second
        assertNull(label.resId)
        assertEquals("Qualche stato strano", label.fallback)
    }

    @Test fun reservationMapsEnglishAndItalianSpellings() {
        assertEquals(AvailabilityStatus.Available, StatusMapping.reservation("attiva").first)
        assertEquals(AvailabilityStatus.Available, StatusMapping.reservation("active").first)
        assertEquals(AvailabilityStatus.DueSoon, StatusMapping.reservation("pending").first)
        assertEquals(AvailabilityStatus.ReservedReady, StatusMapping.reservation("ready").first)
    }

    @Test fun reservationUnknownFallsBackToTitleCasedRaw() {
        val label = StatusMapping.reservation("foo_bar").second
        assertNull(label.resId)
        assertEquals("Foo bar", label.fallback)
    }

    @Test fun loanGroupOrdersUrgentStatesFirst() {
        assertEquals(StatusMapping.LoanGroup.Overdue, StatusMapping.loanGroup("in_ritardo"))
        assertEquals(StatusMapping.LoanGroup.Overdue, StatusMapping.loanGroup("perso"))
        assertEquals(StatusMapping.LoanGroup.OnLoan, StatusMapping.loanGroup("in_corso"))
        assertEquals(StatusMapping.LoanGroup.ReadyScheduled, StatusMapping.loanGroup("prenotato"))
        assertEquals(StatusMapping.LoanGroup.Pending, StatusMapping.loanGroup("pendente"))
    }

    @Test fun loanGroupUnknownDefaultsToPending() {
        assertEquals(StatusMapping.LoanGroup.Pending, StatusMapping.loanGroup("whatever"))
    }

    @Test fun loanGroupOrderIsOverdueBeforeOnLoanBeforeReadyBeforePending() {
        assertTrue(StatusMapping.LoanGroup.Overdue.order < StatusMapping.LoanGroup.OnLoan.order)
        assertTrue(StatusMapping.LoanGroup.OnLoan.order < StatusMapping.LoanGroup.ReadyScheduled.order)
        assertTrue(StatusMapping.LoanGroup.ReadyScheduled.order < StatusMapping.LoanGroup.Pending.order)
    }
}
