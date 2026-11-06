package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Pinakes 0.9.0 patron tools (Mobile API 1.10.0, tags staff-patrons, staff-ledger,
// staff-attendance, staff-user-import). Every field has a default: older servers omit the
// newer keys, and the server is free to add more. Money is always integer cents in the
// server's `currency`.

// ---------- Patron record (StaffUser 1.10.0 fields) ----------

/** The effective patron type of an account; [assigned] false = it follows the default type. */
@Serializable
data class StaffPatronTypeRef(
    val id: Int = 0,
    val code: String = "",
    val name: String = "",
    val assigned: Boolean = false,
)

/** The balance summary on a StaffUser; null while the fines module is off. */
@Serializable
data class StaffLedgerSummary(
    @SerialName("balance_cents") val balanceCents: Int = 0,
    val currency: String? = null,
    @SerialName("debt_blocked") val debtBlocked: Boolean = false,
)

/** `late_fine` of a staff loan (detail and return answers); null while fines are off. */
@Serializable
data class StaffLoanLateFine(
    val currency: String? = null,
    @SerialName("automatic_fines") val automaticFines: Boolean = false,
    @SerialName("accruing_days") val accruingDays: Int = 0,
    /** What a return today would charge: shown before confirming, with the waiver option. */
    @SerialName("accruing_cents") val accruingCents: Int = 0,
    @SerialName("charged_cents") val chargedCents: Int = 0,
)

/** One entry of `GET /staff/patron-types`. */
@Serializable
data class PatronType(
    val id: Int = 0,
    val code: String = "",
    val name: String = "",
    val description: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    /** Inactive types cannot be assigned anew; an account keeps the one it has. */
    val active: Boolean = true,
    @SerialName("card_duration_days") val cardDurationDays: Int? = null,
)

@Serializable
data class PatronTypesMeta(
    @SerialName("default_id") val defaultId: Int? = null,
    @SerialName("can_assign") val canAssign: Boolean = false,
)

/** The picker: the types plus whether this operator may assign them. */
data class PatronTypeList(val types: List<PatronType>, val defaultId: Int?, val canAssign: Boolean)

/** `PUT /staff/users/{id}/patron-type`. 0 = the default type (the server reads 0 as null). */
@Serializable
data class PatronTypeRequest(
    @SerialName("patron_type_id") val patronTypeId: Int,
    @SerialName("recompute_card_expiry") val recomputeCardExpiry: Boolean = false,
)

@Serializable
data class PatronTypeUpdateMeta(@SerialName("card_expiry_recomputed") val cardExpiryRecomputed: Boolean = false)

/** The user after a patron type change, and whether its card expiry was set from the type. */
data class PatronTypeUpdate(val user: StaffUser, val cardExpiryRecomputed: Boolean)

/** An envelope whose `meta` carries endpoint-specific keys (patron types, patron type change). */
@Serializable
data class MetaEnvelope<T, M>(
    val data: T? = null,
    val meta: M? = null,
    val error: ApiError? = null,
)

/** `POST|DELETE /staff/users/{id}/photo`. */
@Serializable
data class PatronPhotoState(
    @SerialName("user_id") val userId: Int = 0,
    @SerialName("has_photo") val hasPhoto: Boolean = false,
    @SerialName("photo_url") val photoUrl: String? = null,
)

// ---------- Ledger ----------

@Serializable
data class PaymentMethod(val code: String = "", val label: String = "")

/** A ledger movement as the desk sees it: the reader's [Movement] plus the operator fields. */
@Serializable
data class StaffLedgerMovement(
    val id: Int = 0,
    val type: String = "",
    @SerialName("type_label") val typeLabel: String? = null,
    /** Signed: positive = charge, negative = payment, waiver or reversal. */
    @SerialName("amount_cents") val amountCents: Int = 0,
    val amount: String? = null,
    val description: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("loan_id") val loanId: Int? = null,
    @SerialName("book_title") val bookTitle: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("payment_method_label") val paymentMethodLabel: String? = null,
    val operator: IdName? = null,
    @SerialName("receipt_available") val receiptAvailable: Boolean = false,
) {
    /** The reader's row, so the reader movements composables draw it (method in words). */
    fun toMovement(): Movement = Movement(
        id = id, type = type, typeLabel = typeLabel, amountCents = amountCents, amount = amount, description = description,
        paymentMethod = paymentMethodLabel ?: paymentMethod, loanId = loanId, bookTitle = bookTitle, createdAt = createdAt,
    )
}

/** `GET /staff/users/{id}/ledger`. */
@Serializable
data class StaffLedger(
    @SerialName("user_id") val userId: Int = 0,
    val currency: String? = null,
    @SerialName("balance_cents") val balanceCents: Int = 0,
    val balance: String? = null,
    @SerialName("accrued_cents") val accruedCents: Int = 0,
    val accrued: List<AccruedFine> = emptyList(),
    @SerialName("debt_block_threshold_cents") val debtBlockThresholdCents: Int? = null,
    @SerialName("debt_blocked") val debtBlocked: Boolean = false,
    @SerialName("automatic_fines") val automaticFines: Boolean = false,
    @SerialName("payment_methods") val paymentMethods: List<PaymentMethod> = emptyList(),
    @SerialName("max_amount_cents") val maxAmountCents: Int = 99_999_999,
    @SerialName("can_record_payment") val canRecordPayment: Boolean = false,
    @SerialName("can_charge") val canCharge: Boolean = false,
    @SerialName("can_waive") val canWaive: Boolean = false,
    val items: List<StaffLedgerMovement> = emptyList(),
) {
    /** The balance card of the reader screen, reused as is. */
    fun toPayload(): MovementsPayload = MovementsPayload(
        currency = currency, balanceCents = balanceCents, balance = balance, accruedCents = accruedCents, accrued = accrued,
        debtBlockThresholdCents = debtBlockThresholdCents, debtBlocked = debtBlocked,
    )
}

/** The answer of a payment, charge or waiver: the movement and the balance after it. */
@Serializable
data class LedgerWriteResult(
    val movement: StaffLedgerMovement = StaffLedgerMovement(),
    @SerialName("user_id") val userId: Int = 0,
    val currency: String? = null,
    @SerialName("balance_cents") val balanceCents: Int = 0,
    val balance: String? = null,
    @SerialName("debt_blocked") val debtBlocked: Boolean = false,
)

@Serializable
data class LedgerPaymentRequest(@SerialName("amount_cents") val amountCents: Int, val method: String, val note: String? = null)

@Serializable
data class LedgerChargeRequest(@SerialName("amount_cents") val amountCents: Int, val description: String)

@Serializable
data class LedgerWaiverRequest(@SerialName("amount_cents") val amountCents: Int, val reason: String)

// ---------- Visitor log ----------

@Serializable
data class AttendanceCounts(val members: Int = 0, val guests: Int = 0, val total: Int = 0)

@Serializable
data class AttendanceDay(val date: String = "", val members: Int = 0, val guests: Int = 0, val total: Int = 0)

@Serializable
data class AttendanceHour(val hour: Int = 0, val total: Int = 0)

@Serializable
data class AttendanceRecent(
    @SerialName("created_at") val createdAt: String = "",
    /** `member` | `guest`. */
    val type: String = "",
    val label: String = "",
)

@Serializable
data class AttendanceRetention(
    /** 0 = visits are kept forever. */
    val days: Int = 365,
    @SerialName("max_days") val maxDays: Int = 3650,
    @SerialName("default_days") val defaultDays: Int = 365,
    @SerialName("continues_when_off") val continuesWhenOff: Boolean = false,
)

/** `GET /staff/attendance`. */
@Serializable
data class AttendanceOverview(
    val days: Int = 30,
    val periods: List<Int> = listOf(7, 30, 90, 365),
    val today: AttendanceCounts = AttendanceCounts(),
    val totals: AttendanceCounts = AttendanceCounts(),
    val daily: List<AttendanceDay> = emptyList(),
    val hourly: List<AttendanceHour> = emptyList(),
    val recent: List<AttendanceRecent> = emptyList(),
    val branches: List<IdName> = emptyList(),
    val retention: AttendanceRetention = AttendanceRetention(),
    @SerialName("can_configure") val canConfigure: Boolean = false,
)

@Serializable
data class AttendanceMember(
    val id: Int = 0,
    @SerialName("short_name") val shortName: String = "",
    @SerialName("card_code") val cardCode: String = "",
    @SerialName("account_status") val accountStatus: String? = null,
    @SerialName("card_active") val cardActive: Boolean = true,
    @SerialName("card_expires_at") val cardExpiresAt: String? = null,
    @SerialName("card_expired") val cardExpired: Boolean = false,
    @SerialName("has_photo") val hasPhoto: Boolean = false,
    @SerialName("photo_url") val photoUrl: String? = null,
)

@Serializable
data class AttendanceGuest(val name: String? = null, val reason: String? = null)

/** One recorded visit (check-in or guest). */
@Serializable
data class AttendanceVisit(
    val id: Int = 0,
    /** `member` | `guest`. */
    val type: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    val branch: IdName? = null,
    val member: AttendanceMember? = null,
    val guest: AttendanceGuest? = null,
    /** `card_not_active`: recorded, but the account is suspended or expired. */
    val warning: String? = null,
    val message: String? = null,
)

@Serializable
data class AttendanceCheckInRequest(
    /** The scanned or typed card; null when [userId] names the member (never both). */
    @SerialName("card_code") val cardCode: String? = null,
    /** Mobile API 1.11.0: a member picked from `GET /staff/users`. */
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("branch_id") val branchId: Int? = null,
)

@Serializable
data class AttendanceGuestRequest(val name: String? = null, val reason: String? = null, @SerialName("branch_id") val branchId: Int? = null)

@Serializable
data class AttendanceSettingsRequest(@SerialName("retention_days") val retentionDays: Int)

@Serializable
data class AttendanceSettingsResult(val retention: AttendanceRetention = AttendanceRetention(), val message: String? = null)

@Serializable
data class AttendancePurgeResult(val deleted: Int = 0, val retention: AttendanceRetention = AttendanceRetention(), val message: String? = null)

// ---------- Patron CSV import ----------

@Serializable
data class UserImportField(val key: String = "", val label: String = "", val required: Boolean = false)

@Serializable
data class UserImportError(val line: Int = 0, val title: String = "", val message: String = "", val type: String = "")

/** A patron import job (`/staff/user-imports/{id}`). */
@Serializable
data class UserImportJob(
    val id: String = "",
    val kind: String = "users",
    @SerialName("file_name") val fileName: String = "",
    /** ready | running | completed | cancelled | failed. */
    val status: String = "ready",
    val started: Boolean = false,
    val complete: Boolean = false,
    @SerialName("total_rows") val totalRows: Int = 0,
    val processed: Int = 0,
    @SerialName("chunk_size") val chunkSize: Int = 10,
    val headers: List<String> = emptyList(),
    val preview: List<List<String>> = emptyList(),
    val fields: List<UserImportField> = emptyList(),
    /** One entry per header: a field key, or "" = ignored. */
    val mapping: List<String> = emptyList(),
    @SerialName("send_invites") val sendInvites: Boolean? = null,
    val created: Int = 0,
    val duplicates: Int = 0,
    val failed: Int = 0,
    val invited: Int = 0,
    @SerialName("invite_failed") val inviteFailed: Int = 0,
    val errors: List<UserImportError> = emptyList(),
    @SerialName("import_id") val importId: String? = null,
    @SerialName("report_available") val reportAvailable: Boolean = false,
    val notice: String? = null,
    @SerialName("log_error") val logError: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class UserImportStartRequest(val mapping: List<String>, @SerialName("send_invites") val sendInvites: Boolean)

@Serializable
data class UserImportChunkRequest(val start: Int)

@Serializable
data class UserImportCancelResult(
    val id: String = "",
    val cancelled: Boolean = false,
    val status: String = "cancelled",
    @SerialName("import_id") val importId: String? = null,
    @SerialName("history_warning") val historyWarning: String? = null,
)
