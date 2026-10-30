package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Staff circulation (Mobile API 1.7.0, tag `staff-circulation`). Every field has a default:
// the server is free to add keys and older builds omit the newer ones. `status` carries the
// raw core enum (`prestiti.stato`, `prenotazioni.stato`), `status_label` its localised text.

/** The book a loan, reservation or copy belongs to. */
@Serializable
data class StaffLoanBook(
    val id: Int = 0,
    val title: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    val isbn13: String? = null,
)

@Serializable
data class StaffLoanCopy(
    val id: Int = 0,
    @SerialName("inventory_number") val inventoryNumber: String? = null,
    val status: String? = null,
)

/** The borrower as the desk sees it. */
@Serializable
data class StaffUserRef(
    val id: Int = 0,
    val name: String = "",
    val email: String? = null,
    @SerialName("card_number") val cardNumber: String? = null,
    /** `attivo` | `sospeso` | `scaduto`. */
    val status: String? = null,
)

@Serializable
data class StaffLoan(
    val id: Int = 0,
    val status: String = "",
    @SerialName("status_label") val statusLabel: String? = null,
    /** `richiesta` | `prenotazione` | `diretto` | `ncip`. */
    val origin: String? = null,
    val book: StaffLoanBook = StaffLoanBook(),
    val copy: StaffLoanCopy? = null,
    val user: StaffUserRef = StaffUserRef(),
    @SerialName("processed_by") val processedBy: IdName? = null,
    @SerialName("requested_at") val requestedAt: String? = null,
    @SerialName("loaned_at") val loanedAt: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("pickup_deadline") val pickupDeadline: String? = null,
    @SerialName("returned_at") val returnedAt: String? = null,
    @SerialName("returned_late") val returnedLate: Boolean = false,
    @SerialName("days_overdue") val daysOverdue: Int = 0,
    val renewals: Int = 0,
    @SerialName("max_renewals") val maxRenewals: Int = 0,
    val penalty: Double? = null,
    val notes: String? = null,
    @SerialName("recall_count") val recallCount: Int = 0,
    @SerialName("last_recall_at") val lastRecallAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** Presentation hint computed server-side; unknown values are ignored by the app. */
    val actions: List<String> = emptyList(),
)

@Serializable
data class StaffReservation(
    val id: Int = 0,
    val status: String = "",
    @SerialName("status_label") val statusLabel: String? = null,
    val book: StaffLoanBook = StaffLoanBook(),
    val user: StaffUserRef = StaffUserRef(),
    @SerialName("queue_position") val queuePosition: Int? = null,
    @SerialName("reserved_at") val reservedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("requested_from") val requestedFrom: String? = null,
    @SerialName("requested_to") val requestedTo: String? = null,
    val notified: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    val actions: List<String> = emptyList(),
)

/** A user found by the desk search, with what the loan form needs to know. */
@Serializable
data class StaffUser(
    val id: Int = 0,
    val name: String = "",
    val email: String? = null,
    @SerialName("card_number") val cardNumber: String? = null,
    val status: String? = null,
    @SerialName("card_expires_at") val cardExpiresAt: String? = null,
    val role: String? = null,
    @SerialName("active_loans") val activeLoans: Int = 0,
    @SerialName("active_reservations") val activeReservations: Int = 0,
    val eligible: Boolean = true,
    @SerialName("eligibility_reason") val eligibilityReason: String? = null,
)

/** `GET /staff/users/{id}`: the user plus open loans and active reservations. */
@Serializable
data class StaffUserDetail(
    val id: Int = 0,
    val name: String = "",
    val email: String? = null,
    @SerialName("card_number") val cardNumber: String? = null,
    val status: String? = null,
    @SerialName("card_expires_at") val cardExpiresAt: String? = null,
    val role: String? = null,
    @SerialName("active_loans") val activeLoans: Int = 0,
    @SerialName("active_reservations") val activeReservations: Int = 0,
    val eligible: Boolean = true,
    @SerialName("eligibility_reason") val eligibilityReason: String? = null,
    val loans: List<StaffLoan> = emptyList(),
    val reservations: List<StaffReservation> = emptyList(),
)

@Serializable
data class CirculationSettings(
    @SerialName("loan_duration_days") val loanDurationDays: Int? = null,
    @SerialName("pickup_expiry_days") val pickupExpiryDays: Int? = null,
    @SerialName("max_renewals") val maxRenewals: Int? = null,
    @SerialName("max_active_loans_per_user") val maxActiveLoansPerUser: Int? = null,
    @SerialName("allow_multiple_loans_same_book") val allowMultipleLoansSameBook: Boolean? = null,
)

/** `GET /staff/circulation/summary`: the desk counters. */
@Serializable
data class CirculationSummary(
    val pending: Int = 0,
    @SerialName("pickup_ready") val pickupReady: Int = 0,
    val scheduled: Int = 0,
    val active: Int = 0,
    val overdue: Int = 0,
    val reservations: Int = 0,
    @SerialName("due_today") val dueToday: Int = 0,
    @SerialName("returned_today") val returnedToday: Int = 0,
    val today: String? = null,
    val settings: CirculationSettings? = null,
)

@Serializable
data class LoanCreateRequest(
    @SerialName("user_id") val userId: Int,
    @SerialName("book_id") val bookId: Int,
    @SerialName("copy_code") val copyCode: String? = null,
    @SerialName("loaned_at") val loanedAt: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    val notes: String? = null,
    /** Always sent: "hand over now" makes the loan `in_corso` straight away. */
    val immediate: Boolean,
)

/** `PUT /staff/loans/{id}`: absent keys keep the stored value (the server merges). */
@Serializable
data class LoanEditRequest(
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("loaned_at") val loanedAt: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
)

@Serializable
data class ReturnRequest(
    val outcome: String,
    val notes: String? = null,
    val penalty: Double? = null,
)

@Serializable
data class ReturnByCodeRequest(
    val code: String,
    val outcome: String? = null,
    val notes: String? = null,
    val penalty: Double? = null,
)

@Serializable
data class ReasonRequest(val reason: String? = null)

@Serializable
data class BulkExtendRequest(val ids: List<Int>, val days: Int)

@Serializable
data class BulkExtendResult(val extended: Int = 0, val days: Int = 0)

@Serializable
data class BulkIdsRequest(val ids: List<Int>)

@Serializable
data class BulkRecallResult(val sent: Int = 0, val skipped: Int = 0)

/** Recall / receipt email: SMTP failures still answer 200 with `sent=false`. */
@Serializable
data class SentResult(val sent: Boolean = false, val message: String? = null)

/** `GET /staff/copies/by-code`. */
@Serializable
data class CopyLookup(
    val found: Boolean = false,
    val copy: StaffCopy? = null,
)

/** `GET /staff/books/{id}/queue`. */
@Serializable
data class BookQueue(
    val reservations: List<StaffReservation> = emptyList(),
    val loans: List<StaffLoan> = emptyList(),
    val copies: List<StaffCopy> = emptyList(),
)

/** Reservation create/edit. On edit absent keys keep the stored value. */
@Serializable
data class ReservationWrite(
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("book_id") val bookId: Int? = null,
    val status: String? = null,
    @SerialName("reserved_at") val reservedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("requested_from") val requestedFrom: String? = null,
    @SerialName("requested_to") val requestedTo: String? = null,
    // Pinakes 0.9.0: staff may ignore the patron type's reservation limits, as the website's
    // "new reservation" form allows. Null is omitted, so an older server never sees the key.
    @SerialName("override_limits") val overrideLimits: Boolean? = null,
)
