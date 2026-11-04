package com.pinakes.app

import com.pinakes.app.data.model.LoanItem
import com.pinakes.app.data.model.StaffLoan
import com.pinakes.app.data.staff.LoanAction
import com.pinakes.app.data.staff.LoanActions
import com.pinakes.app.ui.common.StatusMapping
import com.pinakes.app.ui.components.AvailabilityStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server, not the phone clock, knows the copy branch calendar. */
class CalendarLoanAlignmentTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun firstOpenReturnDayUsesServerDateAndClearsAStaleLateStatus() {
        val loan = json.decodeFromString<LoanItem>("""{
            "id":7,"status":"in_ritardo","due_at":"2030-12-25",
            "effective_due_at":"2030-12-27","is_overdue":false,"due_attention":false
        }""")
        assertEquals("2030-12-25", loan.dueAt)
        assertEquals("2030-12-27", loan.displayDueAt)
        assertEquals(StatusMapping.LoanGroup.OnLoan, StatusMapping.loanGroup(loan))
        assertEquals(AvailabilityStatus.Available, StatusMapping.loan(loan).first)
        assertEquals(StatusMapping.LoanDate(StatusMapping.LoanDateKind.Due, "2030-12-27"), StatusMapping.loanDate(loan))
        assertFalse(StatusMapping.loanNeedsAttention(loan))
    }

    @Test fun overdueDecisionWinsOverAnUnreconciledActiveStatus() {
        val loan = LoanItem(status = "in_corso", dueAt = "2030-12-25", effectiveDueAt = "2030-12-27", isOverdue = true)
        assertEquals(StatusMapping.LoanGroup.Overdue, StatusMapping.loanGroup(loan))
        assertEquals(AvailabilityStatus.Overdue, StatusMapping.loan(loan).first)
        assertEquals(StatusMapping.LoanDate(StatusMapping.LoanDateKind.Overdue, "2030-12-27"), StatusMapping.loanDate(loan))
        assertTrue(StatusMapping.loanNeedsAttention(loan))
    }

    @Test fun dueTodayAttentionDoesNotTurnAnOnTimeLoanIntoAnOverdueOne() {
        val loan = LoanItem(status = "in_corso", isOverdue = false, dueAttention = true)
        assertTrue(StatusMapping.loanNeedsAttention(loan))
        assertEquals(StatusMapping.LoanGroup.OnLoan, StatusMapping.loanGroup(loan))
        assertEquals(R.string.loan_status_on_loan, StatusMapping.loan(loan).second.resId)
    }

    @Test fun olderReaderPayloadKeepsItsPreviousStatusAndDate() {
        val loan = json.decodeFromString<LoanItem>("""{"status":"in_ritardo","due_at":"2030-12-25"}""")
        assertNull(loan.isOverdue)
        assertNull(loan.effectiveDueAt)
        assertEquals(StatusMapping.LoanGroup.Overdue, StatusMapping.loanGroup(loan))
        assertEquals("2030-12-25", loan.displayDueAt)
    }

    @Test fun historyAndUncollectedRequestsKeepTheirOwnTimeline() {
        val returned = LoanItem(status = "restituito", dueAt = "2030-12-25", effectiveDueAt = "2030-12-27", isOverdue = false, returnedAt = "2030-12-24")
        assertEquals(StatusMapping.LoanDate(StatusMapping.LoanDateKind.Returned, "2030-12-24"), StatusMapping.loanDate(returned))
        val pending = LoanItem(status = "pendente", requestedAt = "2030-12-20", effectiveDueAt = "2030-12-27", isOverdue = false)
        assertEquals(StatusMapping.LoanDate(StatusMapping.LoanDateKind.Requested, "2030-12-20"), StatusMapping.loanDate(pending))
    }

    @Test fun staffCanRenewOnTheFirstOpenDayWithoutRewritingTheStoredEditDate() {
        val loan = json.decodeFromString<StaffLoan>("""{
            "id":7,"status":"in_ritardo","due_at":"2030-12-25",
            "effective_due_at":"2030-12-27","is_overdue":false,"days_overdue":0,
            "actions":["return","renew","edit","receipt"]
        }""")
        assertEquals("2030-12-25", loan.dueAt)
        assertEquals("2030-12-27", loan.displayDueAt)
        assertEquals(AvailabilityStatus.Available, StatusMapping.loan(loan).first)
        assertTrue(LoanActions.of(loan).contains(LoanAction.RENEW))
        assertFalse(LoanActions.of(loan).contains(LoanAction.RECALL))
    }

    @Test fun staffOverdueStatusUsesTheServerCalendarAndItsActionList() {
        val loan = json.decodeFromString<StaffLoan>("""{
            "status":"in_corso","due_at":"2030-12-25","effective_due_at":"2030-12-27",
            "is_overdue":true,"days_overdue":1,"actions":["return","recall"]
        }""")
        assertEquals(AvailabilityStatus.Overdue, StatusMapping.loan(loan).first)
        assertEquals(1, loan.daysOverdue)
        assertTrue(LoanActions.of(loan).contains(LoanAction.RECALL))
        assertFalse(LoanActions.of(loan).contains(LoanAction.RENEW))
    }

    @Test fun olderStaffPayloadKeepsItsDateStatusAndActions() {
        val loan = json.decodeFromString<StaffLoan>("""{"status":"in_ritardo","due_at":"2030-12-25","actions":["recall"]}""")
        assertNull(loan.isOverdue)
        assertEquals("2030-12-25", loan.displayDueAt)
        assertEquals(AvailabilityStatus.Overdue, StatusMapping.loan(loan).first)
        assertEquals(listOf(LoanAction.RECALL), LoanActions.of(loan))
    }

    @Test fun futureUnknownStatusesKeepTheirServerLabel() {
        val loan = LoanItem(status = "future_state", statusLabel = "Future loan", isOverdue = false)
        assertEquals("Future loan", StatusMapping.loan(loan).second.fallback)
    }
}
