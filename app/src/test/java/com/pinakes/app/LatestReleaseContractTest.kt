package com.pinakes.app

import com.pinakes.app.data.model.CirculationRequestKind
import com.pinakes.app.data.model.CirculationRequestResult
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.HealthPayload
import com.pinakes.app.data.model.LoanItem
import com.pinakes.app.ui.common.StatusMapping
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wire and presentation regressions for Pinakes 0.7.59–0.7.68 / Mobile API 1.4.4. */
class LatestReleaseContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test fun requestResultKeepsThe384ReservationOutcome() {
        val envelope = json.decodeFromString<Envelope<CirculationRequestResult>>(
            """{"data":{"type":"reservation","book_id":381},"meta":{},"error":null}""",
        )

        assertEquals(CirculationRequestKind.Reservation, envelope.data?.kind)
        assertEquals(381, envelope.data?.bookId)
    }

    @Test fun requestResultDistinguishesPendingAndReadyLoans() {
        val pending = CirculationRequestResult(type = "loan", status = "pendente", autoApproved = false)
        val ready = CirculationRequestResult(type = "loan", status = "da_ritirare", autoApproved = true)

        assertEquals(CirculationRequestKind.LoanPending, pending.kind)
        assertEquals(CirculationRequestKind.LoanReadyForPickup, ready.kind)
        assertEquals(CirculationRequestKind.Unknown, CirculationRequestResult(type = "future").kind)
        assertEquals(
            CirculationRequestKind.Unknown,
            CirculationRequestResult(type = "loan", status = "future_status").kind,
        )
    }

    @Test fun loanPayloadReadsRecentAdditiveFields() {
        val envelope = json.decodeFromString<Envelope<LoanItem>>(
            """{"data":{"id":7,"book_id":9,"title":"T","status":"da_ritirare","status_label":"Da ritirare","requested_at":"2026-08-27","due_attention":true,"cancellable":true},"error":null}""",
        )
        val loan = requireNotNull(envelope.data)

        assertEquals("Da ritirare", loan.statusLabel)
        assertEquals("2026-08-27", loan.requestedAt)
        assertTrue(loan.dueAttention)
        assertTrue(loan.cancellable)
    }

    @Test fun cancelledAndPendingRowsUseRequestDateInsteadOfFakeDueDate() {
        val cancelled = LoanItem(
            status = "annullato",
            requestedAt = "2026-08-20",
            loanedAt = "2026-08-25",
            dueAt = "2026-09-25",
            returnedAt = "2026-08-26",
        )
        val pending = cancelled.copy(status = "pendente")

        assertEquals(StatusMapping.LoanDateKind.Requested, StatusMapping.loanDate(cancelled)?.kind)
        assertEquals("2026-08-20", StatusMapping.loanDate(cancelled)?.value)
        assertEquals(StatusMapping.LoanDateKind.Requested, StatusMapping.loanDate(pending)?.kind)
    }

    @Test fun dueAttentionUsesTheLibraryTimezoneCue() {
        assertTrue(StatusMapping.loanNeedsAttention(LoanItem(status = "in_corso", dueAttention = true)))
        assertFalse(StatusMapping.loanNeedsAttention(LoanItem(status = "in_corso", dueAttention = false)))
    }

    @Test fun unknownLoanStatusUsesServerFallbackLabel() {
        val label = StatusMapping.loan("nuovo_stato", "Etichetta server").second
        assertEquals("Etichetta server", label.fallback)
    }

    @Test fun healthReadsApprovalModeWithoutBreakingOlderServers() {
        val recent = json.decodeFromString<Envelope<HealthPayload>>(
            """{"data":{"loan_approval_required":false},"error":null}""",
        )
        val legacy = json.decodeFromString<Envelope<HealthPayload>>(
            """{"data":{},"error":null}""",
        )

        assertFalse(requireNotNull(recent.data).loanApprovalRequired)
        assertTrue(requireNotNull(legacy.data).loanApprovalRequired)
    }
}
