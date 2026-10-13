package com.pinakes.app.data.repository

import com.pinakes.app.data.local.CachedLoan
import com.pinakes.app.data.local.LibraryDao
import com.pinakes.app.data.local.toCached
import com.pinakes.app.data.local.toLoansData
import com.pinakes.app.data.local.toReservationItem
import com.pinakes.app.data.model.CirculationRequestResult
import com.pinakes.app.data.model.LoansData
import com.pinakes.app.data.model.ReservationItem
import com.pinakes.app.data.model.ReservationRequest
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.apiCall

/**
 * The user's loans and reservations: list active/pending/history loans, list reservations,
 * create a reservation (409 on overlap/unavailable) and cancel a pending one (409 if picked up).
 */
class LibraryRepository(
    private val network: NetworkModule,
    /** Offline copy of the lists; null in tests that only exercise the network path. */
    private val cache: LibraryDao? = null,
) {

    /** Loans; a successful fetch is written through to the offline cache. */
    suspend fun loans(): ApiResult<LoansData> {
        val api = network.api()
        val res = apiCall { api.loans() }
        if (res is ApiResult.Success) {
            runCatching { cache?.replaceLoans(CachedLoan.LOAN_BUCKETS, res.data.toCached()) }
        }
        return res
    }

    /** Reservations; a successful fetch is written through to the offline cache. */
    suspend fun reservations(): ApiResult<List<ReservationItem>> {
        val api = network.api()
        val res = apiCall { api.reservations() }
        if (res is ApiResult.Success) {
            runCatching {
                cache?.replaceLoans(listOf(CachedLoan.RESERVATION), res.data.mapIndexed { i, r -> r.toCached(i) })
            }
        }
        return res
    }

    /** The last loans the server returned, or null when nothing was ever cached. */
    suspend fun loansCached(): LoansData? {
        val rows = cache?.loans(CachedLoan.LOAN_BUCKETS) ?: return null
        return if (rows.isEmpty()) null else rows.toLoansData()
    }

    suspend fun reservationsCached(): List<ReservationItem> =
        cache?.loans(listOf(CachedLoan.RESERVATION))?.map { it.toReservationItem() }.orEmpty()

    /** Background refresh of both lists (sync worker); failures are ignored. */
    suspend fun refreshCache() {
        loans()
        reservations()
    }

    /**
     * Request a loan/reservation for [bookId] starting on [desiredDate] (yyyy-MM-dd). The
     * backend computes the return date as +1 month, matching the website behaviour.
     */
    suspend fun reserve(
        bookId: Int,
        desiredDate: String? = null,
    ): ApiResult<CirculationRequestResult> {
        val api = network.api()
        val res = apiCall { api.createReservation(ReservationRequest(bookId = bookId, desiredDate = desiredDate)) }
        // The request lands in pending loans or in reservations: refresh the offline copy.
        if (res is ApiResult.Success) runCatching { refreshCache() }
        return res
    }

    suspend fun cancelReservation(reservationId: Int): ApiResult<Unit> {
        val api = network.api()
        val res = apiCall { api.cancelReservation(reservationId) }
        if (res is ApiResult.Success) runCatching { cache?.deleteLoan(reservationId, listOf(CachedLoan.RESERVATION)) }
        return res
    }

    suspend fun cancelLoan(loanId: Int): ApiResult<Unit> {
        val api = network.api()
        val res = apiCall { api.cancelLoan(loanId) }
        // A cancelled request leaves the current lists (the next fetch files it in history).
        if (res is ApiResult.Success) runCatching { cache?.deleteLoan(loanId, listOf(CachedLoan.PENDING, CachedLoan.ACTIVE)) }
        return res
    }
}
