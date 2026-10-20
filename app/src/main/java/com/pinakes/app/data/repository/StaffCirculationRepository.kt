package com.pinakes.app.data.repository

import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.BookQueue
import com.pinakes.app.data.model.BulkExtendRequest
import com.pinakes.app.data.model.BulkExtendResult
import com.pinakes.app.data.model.BulkIdsRequest
import com.pinakes.app.data.model.BulkRecallResult
import com.pinakes.app.data.model.CirculationSummary
import com.pinakes.app.data.model.CopyLookup
import com.pinakes.app.data.model.LoanCreateRequest
import com.pinakes.app.data.model.LoanEditRequest
import com.pinakes.app.data.model.ReasonRequest
import com.pinakes.app.data.model.ReservationWrite
import com.pinakes.app.data.model.ReturnByCodeRequest
import com.pinakes.app.data.model.ReturnRequest
import com.pinakes.app.data.model.SentResult
import com.pinakes.app.data.model.StaffLoan
import com.pinakes.app.data.model.StaffReservation
import com.pinakes.app.data.model.StaffUser
import com.pinakes.app.data.model.StaffUserDetail
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.staff.CopyCodeCodec
import com.pinakes.app.data.store.SessionStore

/** The loan list buckets of `GET /staff/loans`. */
enum class LoanBucket(val wire: String) {
    PENDING("pending"), PICKUP("pickup"), SCHEDULED("scheduled"), ACTIVE("active"), OVERDUE("overdue"), HISTORY("history"), ALL("all");

    companion object {
        fun fromWire(value: String?): LoanBucket = entries.firstOrNull { it.wire == value } ?: ALL
    }
}

/**
 * The staff circulation desk (Mobile API 1.7.0). Every call goes through [staffCall], so a
 * demoted operator loses the staff area on the first refused call. Writes answer the
 * reloaded resource; the screens show it as is.
 */
class StaffCirculationRepository(
    private val network: NetworkModule,
    private val session: SessionStore,
) {
    companion object {
        /** Server limits of `POST /staff/loans/bulk-extend` and `/bulk-recall`. */
        const val BULK_EXTEND_MAX = 500
        const val BULK_RECALL_MAX = 50
    }

    private val api get() = network.staffCirculationApi()

    suspend fun summary(): ApiResult<CirculationSummary> = session.staffCall { api.summary() }

    suspend fun loans(
        bucket: LoanBucket,
        query: String? = null,
        cursor: String? = null,
        userId: Int? = null,
        bookId: Int? = null,
        limit: Int = 30,
    ): ApiResult<StaffPage<StaffLoan>> = session.staffCall {
        api.loans(bucket = bucket.wire, q = query.blankToNull(), userId = userId, bookId = bookId, cursor = cursor, limit = limit)
    }.toPage()

    suspend fun loan(id: Int): ApiResult<StaffLoan> = session.staffCall { api.loan(id) }

    /**
     * [idempotencyKey] identifies this submission: the same key on every retry of it, so a
     * request that reached the server before the connection dropped is not created twice.
     */
    suspend fun createLoan(request: LoanCreateRequest, idempotencyKey: String? = null): ApiResult<StaffLoan> =
        session.staffCall {
            api.createLoan(request.copy(copyCode = request.copyCode?.let(CopyCodeCodec::normalize), notes = request.notes.blankToNull()), idempotencyKey)
        }

    suspend fun updateLoan(id: Int, request: LoanEditRequest): ApiResult<StaffLoan> = session.staffCall { api.updateLoan(id, request) }

    suspend fun approve(id: Int): ApiResult<StaffLoan> = session.staffCall { api.approve(id) }

    suspend fun reject(id: Int, reason: String?): ApiResult<StaffLoan> =
        session.staffCall { api.reject(id, ReasonRequest(reason.blankToNull()?.take(500))) }

    suspend fun pickup(id: Int): ApiResult<StaffLoan> = session.staffCall { api.pickup(id) }

    suspend fun cancelPickup(id: Int, reason: String?): ApiResult<StaffLoan> =
        session.staffCall { api.cancelPickup(id, ReasonRequest(reason.blankToNull()?.take(500))) }

    suspend fun returnLoan(id: Int, outcome: String, notes: String?, penalty: Double?): ApiResult<StaffLoan> =
        session.staffCall { api.returnLoan(id, ReturnRequest(outcome, notes.blankToNull(), penalty)) }

    suspend fun returnByCode(code: String, outcome: String, notes: String?, penalty: Double?): ApiResult<StaffLoan> =
        session.staffCall { api.returnByCode(ReturnByCodeRequest(CopyCodeCodec.normalize(code), outcome, notes.blankToNull(), penalty)) }

    suspend fun renew(id: Int): ApiResult<StaffLoan> = session.staffCall { api.renew(id) }

    /**
     * Extend every selected loan: the server takes at most [BULK_EXTEND_MAX] ids per call, so a
     * larger selection goes in batches and the counts are summed. A failing batch stops there
     * and reports the failure (the earlier batches are already applied server-side).
     */
    suspend fun bulkExtend(ids: List<Int>, days: Int): ApiResult<BulkExtendResult> {
        val d = days.coerceIn(1, 365)
        var extended = 0
        for (batch in ids.distinct().chunked(BULK_EXTEND_MAX)) {
            when (val res = session.staffCall { api.bulkExtend(BulkExtendRequest(batch, d)) }) {
                is ApiResult.Success -> extended += res.data.extended
                is ApiResult.Failure -> return res
            }
        }
        return ApiResult.Success(BulkExtendResult(extended, d))
    }

    suspend fun recall(id: Int): ApiResult<SentResult> = session.staffCall { api.recall(id) }

    /** Remind every selected borrower, in batches of [BULK_RECALL_MAX] (the server's limit), summing the counts. */
    suspend fun bulkRecall(ids: List<Int>): ApiResult<BulkRecallResult> {
        var sent = 0
        var skipped = 0
        for (batch in ids.distinct().chunked(BULK_RECALL_MAX)) {
            when (val res = session.staffCall { api.bulkRecall(BulkIdsRequest(batch)) }) {
                is ApiResult.Success -> { sent += res.data.sent; skipped += res.data.skipped }
                is ApiResult.Failure -> return res
            }
        }
        return ApiResult.Success(BulkRecallResult(sent, skipped))
    }

    suspend fun emailReceipt(id: Int): ApiResult<SentResult> = session.staffCall { api.emailReceipt(id) }

    suspend fun reservations(
        status: String,
        query: String? = null,
        cursor: String? = null,
        bookId: Int? = null,
        userId: Int? = null,
        limit: Int = 50,
    ): ApiResult<StaffPage<StaffReservation>> = session.staffCall {
        api.reservations(status = status, q = query.blankToNull(), bookId = bookId, userId = userId, cursor = cursor, limit = limit)
    }.toPage()

    suspend fun createReservation(body: ReservationWrite, idempotencyKey: String? = null): ApiResult<StaffReservation> =
        session.staffCall { api.createReservation(body, idempotencyKey) }

    suspend fun updateReservation(id: Int, body: ReservationWrite): ApiResult<StaffReservation> =
        session.staffCall { api.updateReservation(id, body) }

    suspend fun cancelReservation(id: Int, reason: String?): ApiResult<StaffReservation> =
        session.staffCall { api.cancelReservation(id, ReasonRequest(reason.blankToNull()?.take(500))) }

    suspend fun queue(bookId: Int): ApiResult<BookQueue> = session.staffCall { api.queue(bookId) }

    suspend fun availability(bookId: Int, userId: Int?): ApiResult<AvailabilityCalendar> =
        session.staffCall { api.availability(bookId, userId?.takeIf { it > 0 }, 180) }

    suspend fun users(query: String, limit: Int = 20): ApiResult<List<StaffUser>> = session.staffCall { api.users(query.trim(), limit) }

    suspend fun user(id: Int): ApiResult<StaffUserDetail> = session.staffCall { api.user(id) }

    suspend fun copyByCode(code: String): ApiResult<CopyLookup> = session.staffCall { api.copyByCode(CopyCodeCodec.normalize(code)) }
}
