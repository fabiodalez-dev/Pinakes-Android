package com.pinakes.app.data.staff

import com.pinakes.app.data.model.StaffLoan

/** The desk actions a loan can offer (`actions` of `StaffLoan`, Mobile API 1.7.0). */
enum class LoanAction(val wire: String) {
    APPROVE("approve"),
    REJECT("reject"),
    CONFIRM_PICKUP("confirm_pickup"),
    CANCEL_PICKUP("cancel_pickup"),
    RETURN("return"),
    RENEW("renew"),
    EDIT("edit"),
    RECALL("recall"),
    RECEIPT("receipt"),
    EMAIL_RECEIPT("email_receipt");

    companion object {
        fun fromWire(value: String): LoanAction? = entries.firstOrNull { it.wire == value.trim().lowercase() }
    }
}

/** What the screen opens for an action. */
enum class LoanActionUi { IMMEDIATE, CONFIRM, REASON, RETURN_SHEET, EDIT_SHEET, DOWNLOAD }

/** The return outcomes of `POST /staff/loans/{id}/return` (the copy's next state). */
enum class ReturnOutcome(val wire: String, val allowsPenalty: Boolean) {
    RETURNED("restituito", false),
    MAINTENANCE("manutenzione", false),
    RESTORATION("in_restauro", false),
    LOST("perso", true),
    DAMAGED("danneggiato", true);

    companion object {
        fun fromWire(value: String?): ReturnOutcome = entries.firstOrNull { it.wire == value } ?: RETURNED
    }
}

/** Penalty input → value to send, or an error. */
sealed interface PenaltyInput {
    data object None : PenaltyInput
    data class Amount(val value: Double) : PenaltyInput
    data object Invalid : PenaltyInput
}

/**
 * The pure rules behind the loan detail actions. The server stays the authority (it re-checks
 * every action); this only decides what to show and how to open it.
 */
object LoanActions {
    /** Largest penalty the server stores (DECIMAL(10,2)). */
    const val MAX_PENALTY = 99_999_999.99

    /** The known actions of [loan], in the server's order; unknown values are ignored. */
    fun of(loan: StaffLoan): List<LoanAction> = loan.actions.mapNotNull(LoanAction::fromWire).distinct()

    /** How each action is presented. */
    fun ui(action: LoanAction): LoanActionUi = when (action) {
        LoanAction.APPROVE, LoanAction.CONFIRM_PICKUP, LoanAction.RENEW -> LoanActionUi.CONFIRM
        LoanAction.REJECT, LoanAction.CANCEL_PICKUP -> LoanActionUi.REASON
        LoanAction.RETURN -> LoanActionUi.RETURN_SHEET
        LoanAction.EDIT -> LoanActionUi.EDIT_SHEET
        LoanAction.RECEIPT -> LoanActionUi.DOWNLOAD
        LoanAction.RECALL, LoanAction.EMAIL_RECEIPT -> LoanActionUi.IMMEDIATE
    }

    /** Only loans that are out (on time or overdue) can be extended or recalled together. */
    fun isBulkSelectable(loan: StaffLoan): Boolean = loan.status == "in_corso" || loan.status == "in_ritardo"

    /**
     * Parse the penalty field: blank → none; a number with `.` or `,` as decimal separator, at
     * most two decimals, between 0 and [MAX_PENALTY]. Only lost/damaged outcomes carry one.
     */
    fun parsePenalty(raw: String, outcome: ReturnOutcome): PenaltyInput {
        val text = raw.trim()
        if (text.isEmpty()) return PenaltyInput.None
        if (!outcome.allowsPenalty) return PenaltyInput.Invalid
        if (!Regex("""^\d{1,8}([.,]\d{1,2})?$""").matches(text)) return PenaltyInput.Invalid
        val value = text.replace(',', '.').toDoubleOrNull() ?: return PenaltyInput.Invalid
        if (value < 0 || value > MAX_PENALTY) return PenaltyInput.Invalid
        return PenaltyInput.Amount(value)
    }
}
