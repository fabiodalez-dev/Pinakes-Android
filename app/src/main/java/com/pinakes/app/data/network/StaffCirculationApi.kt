package com.pinakes.app.data.network

import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.BookQueue
import com.pinakes.app.data.model.BulkExtendRequest
import com.pinakes.app.data.model.BulkExtendResult
import com.pinakes.app.data.model.BulkIdsRequest
import com.pinakes.app.data.model.BulkRecallResult
import com.pinakes.app.data.model.CirculationSummary
import com.pinakes.app.data.model.CopyLookup
import com.pinakes.app.data.model.Envelope
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
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Staff circulation desk (Mobile API 1.7.0). Same base URL and bearer token as [PinakesApi];
 * every route answers 403 `forbidden` to non-staff and 404 `feature_disabled` in catalogue mode.
 */
interface StaffCirculationApi {

    @GET("staff/circulation/summary")
    suspend fun summary(): Envelope<CirculationSummary>

    @GET("staff/loans")
    suspend fun loans(
        @Query("bucket") bucket: String? = null,
        @Query("q") q: String? = null,
        @Query("user_id") userId: Int? = null,
        @Query("book_id") bookId: Int? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Envelope<List<StaffLoan>>

    @GET("staff/loans/{id}")
    suspend fun loan(@Path("id") id: Int): Envelope<StaffLoan>

    @POST("staff/loans")
    suspend fun createLoan(@Body body: LoanCreateRequest): Envelope<StaffLoan>

    @PUT("staff/loans/{id}")
    suspend fun updateLoan(@Path("id") id: Int, @Body body: LoanEditRequest): Envelope<StaffLoan>

    @POST("staff/loans/{id}/approve")
    suspend fun approve(@Path("id") id: Int): Envelope<StaffLoan>

    @POST("staff/loans/{id}/reject")
    suspend fun reject(@Path("id") id: Int, @Body body: ReasonRequest): Envelope<StaffLoan>

    @POST("staff/loans/{id}/pickup")
    suspend fun pickup(@Path("id") id: Int): Envelope<StaffLoan>

    @POST("staff/loans/{id}/cancel-pickup")
    suspend fun cancelPickup(@Path("id") id: Int, @Body body: ReasonRequest): Envelope<StaffLoan>

    @POST("staff/loans/{id}/return")
    suspend fun returnLoan(@Path("id") id: Int, @Body body: ReturnRequest): Envelope<StaffLoan>

    @POST("staff/loans/return-by-code")
    suspend fun returnByCode(@Body body: ReturnByCodeRequest): Envelope<StaffLoan>

    @POST("staff/loans/{id}/renew")
    suspend fun renew(@Path("id") id: Int): Envelope<StaffLoan>

    @POST("staff/loans/bulk-extend")
    suspend fun bulkExtend(@Body body: BulkExtendRequest): Envelope<BulkExtendResult>

    @POST("staff/loans/{id}/recall")
    suspend fun recall(@Path("id") id: Int): Envelope<SentResult>

    @POST("staff/loans/bulk-recall")
    suspend fun bulkRecall(@Body body: BulkIdsRequest): Envelope<BulkRecallResult>

    @POST("staff/loans/{id}/receipt/email")
    suspend fun emailReceipt(@Path("id") id: Int): Envelope<SentResult>

    @GET("staff/reservations")
    suspend fun reservations(
        @Query("status") status: String? = null,
        @Query("q") q: String? = null,
        @Query("book_id") bookId: Int? = null,
        @Query("user_id") userId: Int? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Envelope<List<StaffReservation>>

    @GET("staff/reservations/{id}")
    suspend fun reservation(@Path("id") id: Int): Envelope<StaffReservation>

    @POST("staff/reservations")
    suspend fun createReservation(@Body body: ReservationWrite): Envelope<StaffReservation>

    @PUT("staff/reservations/{id}")
    suspend fun updateReservation(@Path("id") id: Int, @Body body: ReservationWrite): Envelope<StaffReservation>

    @POST("staff/reservations/{id}/cancel")
    suspend fun cancelReservation(@Path("id") id: Int, @Body body: ReasonRequest): Envelope<StaffReservation>

    @GET("staff/books/{id}/queue")
    suspend fun queue(@Path("id") bookId: Int): Envelope<BookQueue>

    @GET("staff/books/{id}/availability")
    suspend fun availability(
        @Path("id") bookId: Int,
        @Query("user_id") userId: Int? = null,
        @Query("days") days: Int? = null,
    ): Envelope<AvailabilityCalendar>

    @GET("staff/users")
    suspend fun users(@Query("q") q: String, @Query("limit") limit: Int? = null): Envelope<List<StaffUser>>

    @GET("staff/users/{id}")
    suspend fun user(@Path("id") id: Int): Envelope<StaffUserDetail>

    @GET("staff/copies/by-code")
    suspend fun copyByCode(@Query("code") code: String): Envelope<CopyLookup>
}
