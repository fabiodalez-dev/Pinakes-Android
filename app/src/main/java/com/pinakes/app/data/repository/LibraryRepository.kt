package com.pinakes.app.data.repository

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
class LibraryRepository(private val network: NetworkModule) {

    suspend fun loans(): ApiResult<LoansData> {
        val api = network.api()
        return apiCall { api.loans() }
    }

    suspend fun reservations(): ApiResult<List<ReservationItem>> {
        val api = network.api()
        return apiCall { api.reservations() }
    }

    /**
     * Request a loan/reservation for [bookId] starting on [desiredDate] (yyyy-MM-dd). The
     * backend computes the return date as +1 month, matching the website behaviour.
     */
    suspend fun reserve(
        bookId: Int,
        desiredDate: String? = null,
    ): ApiResult<Unit> {
        val api = network.api()
        return apiCall { api.createReservation(ReservationRequest(bookId = bookId, desiredDate = desiredDate)) }
    }

    suspend fun cancelReservation(reservationId: Int): ApiResult<Unit> {
        val api = network.api()
        return apiCall { api.cancelReservation(reservationId) }
    }
}
