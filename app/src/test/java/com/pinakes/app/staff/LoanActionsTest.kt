package com.pinakes.app.staff

import com.pinakes.app.data.model.StaffLoan
import com.pinakes.app.data.model.StaffLoanLateFine
import com.pinakes.app.data.staff.LoanAction
import com.pinakes.app.data.staff.LoanActionUi
import com.pinakes.app.data.staff.LoanActions
import com.pinakes.app.data.staff.ReturnOutcome
import com.pinakes.app.ui.screens.staff.circulation.LoanEditDraft
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

    private val accruing = StaffLoanLateFine(currency = "EUR", automaticFines = true, accruingDays = 4, accruingCents = 200)
    private val overdue = StaffLoan(status = "in_ritardo", dueAt = "2026-11-10", lateFine = accruing)

    @Test fun movingAnOverdueLoanForwardWithAFineAccruingNeedsAReason() {
        assertTrue(LoanActions.extensionNeedsReason(overdue, "2026-11-30"))
        // The server reads only the date part of the stored due date.
        assertTrue(LoanActions.extensionNeedsReason(overdue.copy(dueAt = "2026-11-10 00:00:00"), "2026-11-11"))
        // The status the core sets before the overdue job runs counts too.
        assertTrue(LoanActions.extensionNeedsReason(overdue.copy(status = "in_corso"), "2026-11-30"))
    }

    @Test fun noReasonWhenTheDueDateDoesNotMoveForwardOrNothingAccrues() {
        assertFalse(LoanActions.extensionNeedsReason(overdue, null))
        assertFalse(LoanActions.extensionNeedsReason(overdue, "2026-11-10"))
        assertFalse(LoanActions.extensionNeedsReason(overdue, "2026-11-05"))
        assertFalse(LoanActions.extensionNeedsReason(overdue.copy(lateFine = null), "2026-11-30"))
        assertFalse(LoanActions.extensionNeedsReason(overdue.copy(lateFine = accruing.copy(accruingCents = 0)), "2026-11-30"))
        assertFalse(LoanActions.extensionNeedsReason(overdue.copy(lateFine = accruing.copy(automaticFines = false)), "2026-11-30"))
        assertFalse(LoanActions.extensionNeedsReason(overdue.copy(status = "restituito"), "2026-11-30"))
        assertFalse(LoanActions.extensionNeedsReason(overdue.copy(dueAt = null), "2026-11-30"))
    }

    @Test fun theEditSheetAsksForTheReasonTheRuleOrTheServerWants() {
        val draft = LoanEditDraft(userId = 1, userName = "R", loanedAt = "2026-10-01", dueAt = "2026-11-30")
        assertTrue(draft.needsReason(overdue))
        assertFalse(draft.copy(dueAt = "2026-11-10").needsReason(overdue))
        assertFalse(draft.needsReason(null))
        // A 422 on waive_reason opens the field even where the app saw no accruing fine.
        assertTrue(draft.copy(reasonAsked = true).needsReason(overdue.copy(lateFine = null)))
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
