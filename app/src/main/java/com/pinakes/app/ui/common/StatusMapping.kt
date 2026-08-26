package com.pinakes.app.ui.common

import androidx.annotation.StringRes
import com.pinakes.app.R
import com.pinakes.app.data.model.LoanItem
import com.pinakes.app.ui.components.AvailabilityStatus

/**
 * A chip/status label that is either a localizable string resource ([resId]) or, for
 * unrecognized backend states, a humanized [fallback] of the raw `stato` value.
 */
data class StatusLabel(@param:StringRes val resId: Int?, val fallback: String? = null)

/**
 * Maps backend loan/reservation `stato` values to a chip status + localizable label.
 *
 * The Pinakes API (`/me/loans`) emits RAW Italian DB enum values in `status`. Every known
 * value is mapped to a colour bucket ([AvailabilityStatus]) and a localized [StatusLabel];
 * unknown values fall through to a title-cased fallback so the chip never renders an
 * untranslated snake_case string.
 */
object StatusMapping {

    fun loan(stato: String, serverLabel: String? = null): Pair<AvailabilityStatus, StatusLabel> = when (stato) {
        // Active, on time — available-green (this is the good state).
        "in_corso" -> AvailabilityStatus.Available to StatusLabel(R.string.loan_status_on_loan)
        // Overdue — RED, the most important alert state.
        "in_ritardo" -> AvailabilityStatus.Overdue to StatusLabel(R.string.loan_status_overdue)
        // Ready for pickup — info / primary-family tint.
        "da_ritirare" -> AvailabilityStatus.ReservedReady to StatusLabel(R.string.loan_status_ready_pickup)
        // Scheduled / reserved-for-a-future-date — neutral info.
        "prenotato" -> AvailabilityStatus.Scheduled to StatusLabel(R.string.loan_status_scheduled)
        // Waiting for librarian approval — amber.
        "pendente" -> AvailabilityStatus.DueSoon to StatusLabel(R.string.loan_status_pending_approval)
        // Terminal states — neutral grey.
        "restituito" -> AvailabilityStatus.Returned to StatusLabel(R.string.loan_status_returned)
        "scaduto" -> AvailabilityStatus.Returned to StatusLabel(R.string.loan_status_expired)
        "annullato" -> AvailabilityStatus.Returned to StatusLabel(R.string.loan_status_cancelled)
        // Damage/loss — amber / red.
        "danneggiato" -> AvailabilityStatus.DueSoon to StatusLabel(R.string.loan_status_damaged)
        "perso" -> AvailabilityStatus.Overdue to StatusLabel(R.string.loan_status_lost)
        // Legacy / older API spellings kept for safety.
        "in_scadenza" -> AvailabilityStatus.DueSoon to StatusLabel(R.string.loan_status_due_soon)
        "concluso" -> AvailabilityStatus.Returned to StatusLabel(R.string.loan_status_returned)
        "in_attesa" -> AvailabilityStatus.DueSoon to StatusLabel(R.string.loan_status_pending_approval)
        else -> AvailabilityStatus.LoanActive to
            StatusLabel(
                null,
                serverLabel?.takeIf { it.isNotBlank() }
                    ?: stato.replace('_', ' ').replaceFirstChar { it.uppercase() },
            )
    }

    fun reservation(stato: String): Pair<AvailabilityStatus, StatusLabel> = when (stato) {
        "attiva", "active" -> AvailabilityStatus.Available to StatusLabel(R.string.reservation_status_active)
        "in_attesa", "pending", "pendente" -> AvailabilityStatus.DueSoon to StatusLabel(R.string.reservation_status_pending)
        "pronto", "ready", "da_ritirare" -> AvailabilityStatus.ReservedReady to StatusLabel(R.string.reservation_status_ready)
        "approvato", "approved" -> AvailabilityStatus.Available to StatusLabel(R.string.reservation_status_approved)
        "annullato", "cancelled" -> AvailabilityStatus.Returned to StatusLabel(R.string.reservation_status_cancelled)
        "scaduto", "expired" -> AvailabilityStatus.Returned to StatusLabel(R.string.reservation_status_expired)
        else -> AvailabilityStatus.ReservedReady to
            StatusLabel(null, stato.replace('_', ' ').replaceFirstChar { it.uppercase() })
    }

    /** A pending reservation can still be cancelled by the user. */
    fun isReservationCancellable(stato: String): Boolean =
        stato in setOf("in_attesa", "pending", "pendente", "attiva", "active", "prenotato")

    /**
     * Priority bucket for the "Active" tab so the user sees the most urgent items first:
     * overdue → on-loan → ready/scheduled → pending-approval. Lower number = higher priority.
     */
    enum class LoanGroup(val order: Int) {
        Overdue(0),       // in_ritardo, perso, danneggiato — needs attention NOW
        OnLoan(1),        // in_corso — in hand, on time
        ReadyScheduled(2),// da_ritirare, prenotato — upcoming
        Pending(3),       // pendente, in_attesa — awaiting approval
    }

    fun loanGroup(stato: String): LoanGroup = when (stato) {
        "in_ritardo", "perso", "danneggiato" -> LoanGroup.Overdue
        "in_corso", "in_scadenza" -> LoanGroup.OnLoan
        "da_ritirare", "prenotato" -> LoanGroup.ReadyScheduled
        "pendente", "in_attesa" -> LoanGroup.Pending
        else -> LoanGroup.Pending
    }

    /** Server computes this in the library timezone; stale `in_corso` rows may need attention too. */
    fun loanNeedsAttention(loan: LoanItem): Boolean =
        loan.dueAttention || loanGroup(loan.status) == LoanGroup.Overdue

    enum class LoanDateKind { Overdue, Returned, Requested, Due, Borrowed }

    data class LoanDate(val kind: LoanDateKind, val value: String? = null)

    /**
     * Choose an honest timeline label for recent Mobile API payloads. In
     * particular, pending/cancelled/expired rows were never borrowed, so their
     * requested_at must win over the requested loan interval's due date.
     */
    fun loanDate(loan: LoanItem): LoanDate? {
        val overdue = loanGroup(loan.status) == LoanGroup.Overdue
        if (overdue) return LoanDate(LoanDateKind.Overdue, loan.dueAt)
        if (loan.status in setOf("pendente", "in_attesa", "annullato", "scaduto")) {
            return loan.requestedAt?.let { LoanDate(LoanDateKind.Requested, it) }
        }
        if (loan.returnedAt != null) return LoanDate(LoanDateKind.Returned, loan.returnedAt)
        if (loan.dueAt != null) return LoanDate(LoanDateKind.Due, loan.dueAt)
        if (loan.loanedAt != null) return LoanDate(LoanDateKind.Borrowed, loan.loanedAt)
        return null
    }
}
