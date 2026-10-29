package com.pinakes.app.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * `GET /me/movements` (Mobile API 1.9.0, Pinakes 0.9.0 fines ledger): the reader's balance
 * (positive = owed to the library), the fines still accruing on overdue loans and the ledger
 * entries, newest first. Every field is optional so a partial payload never crashes the page;
 * a server without the ledger answers 404 `feature_disabled` and the page is not offered.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MovementsPayload(
    val currency: String? = null,
    @SerialName("balance_cents") val balanceCents: Int = 0,
    /** Decimal string in [currency] ("4.50"); the app formats [balanceCents] when it is missing. */
    val balance: String? = null,
    /** Null when the server omits it: [accruedTotalCents] then adds up [accrued]. */
    @SerialName("accrued_cents") val accruedCents: Int? = null,
    val accrued: List<AccruedFine> = emptyList(),
    @SerialName("debt_block_threshold_cents") val debtBlockThresholdCents: Int? = null,
    @SerialName("debt_blocked") val debtBlocked: Boolean = false,
    /** The server names the list `items`; the 0.9.0 plan called it `movements`: both are read. */
    @JsonNames("movements") val items: List<Movement> = emptyList(),
) {
    /** The fines still accruing: the server's total, else the sum of its rows (an explicit 0 stays 0). */
    val accruedTotalCents: Int get() = accruedCents ?: accrued.sumOf { it.amountCents }
}

/** One ledger entry: a charge (positive amount), a payment, waiver or reversal (negative). */
@Serializable
data class Movement(
    val id: Int = 0,
    /** multa_ritardo | addebito_perdita | addebito_danno | addebito_manuale | pagamento | condono | storno */
    val type: String = "",
    @SerialName("type_label") val typeLabel: String? = null,
    @SerialName("amount_cents") val amountCents: Int = 0,
    val amount: String? = null,
    val description: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("loan_id") val loanId: Int? = null,
    @SerialName("book_title") val bookTitle: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** A late fine still growing on an overdue loan: charged only when the book comes back. */
@Serializable
data class AccruedFine(
    @SerialName("loan_id") val loanId: Int = 0,
    val title: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("days_late") val daysLate: Int = 0,
    @SerialName("amount_cents") val amountCents: Int = 0,
)
