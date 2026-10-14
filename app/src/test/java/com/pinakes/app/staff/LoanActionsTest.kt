package com.pinakes.app.staff

import com.pinakes.app.data.model.StaffLoan
import com.pinakes.app.data.staff.LoanAction
import com.pinakes.app.data.staff.LoanActionUi
import com.pinakes.app.data.staff.LoanActions
import com.pinakes.app.data.staff.ReturnOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which loan actions show, and how each opens. The server stays the authority. */
class LoanActionsTest {

    @Test fun everyWireValueRoundTrips() {
        LoanAction.entries.forEach { assertEquals(it, LoanAction.fromWire(it.wire)) }
        assertEquals(LoanAction.CONFIRM_PICKUP, LoanAction.fromWire(" Confirm_Pickup "))
        assertNull(LoanAction.fromWire("teleport"))
    }

    @Test fun unknownAndDuplicateActionsAreDropped() {
        val loan = StaffLoan(actions = listOf("approve", "approve", "unknown", "reject"))
        assertEquals(listOf(LoanAction.APPROVE, LoanAction.REJECT), LoanActions.of(loan))
    }

    @Test fun presentation() {
        assertEquals(LoanActionUi.CONFIRM, LoanActions.ui(LoanAction.APPROVE))
        assertEquals(LoanActionUi.REASON, LoanActions.ui(LoanAction.REJECT))
        assertEquals(LoanActionUi.REASON, LoanActions.ui(LoanAction.CANCEL_PICKUP))
        assertEquals(LoanActionUi.RETURN_SHEET, LoanActions.ui(LoanAction.RETURN))
        assertEquals(LoanActionUi.EDIT_SHEET, LoanActions.ui(LoanAction.EDIT))
        assertEquals(LoanActionUi.DOWNLOAD, LoanActions.ui(LoanAction.RECEIPT))
        assertEquals(LoanActionUi.IMMEDIATE, LoanActions.ui(LoanAction.RECALL))
        assertEquals(LoanActionUi.IMMEDIATE, LoanActions.ui(LoanAction.EMAIL_RECEIPT))
    }

    @Test fun onlyOutstandingLoansAreBulkSelectable() {
        assertTrue(LoanActions.isBulkSelectable(StaffLoan(status = "in_corso")))
        assertTrue(LoanActions.isBulkSelectable(StaffLoan(status = "in_ritardo")))
        listOf("pendente", "da_ritirare", "prenotato", "restituito", "perso").forEach {
            assertFalse(it, LoanActions.isBulkSelectable(StaffLoan(status = it)))
        }
    }

    @Test fun returnOutcomes() {
        assertEquals(ReturnOutcome.RETURNED, ReturnOutcome.fromWire(null))
        assertEquals(ReturnOutcome.DAMAGED, ReturnOutcome.fromWire("danneggiato"))
        assertEquals(setOf(ReturnOutcome.LOST, ReturnOutcome.DAMAGED), ReturnOutcome.entries.filter { it.allowsPenalty }.toSet())
        assertEquals(listOf("restituito", "manutenzione", "in_restauro", "perso", "danneggiato"), ReturnOutcome.entries.map { it.wire })
    }
}
