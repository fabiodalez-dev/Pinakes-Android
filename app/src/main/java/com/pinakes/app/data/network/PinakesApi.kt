package com.pinakes.app.data.network

import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.BookDetail
import com.pinakes.app.data.model.BookReviews
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.data.model.ChangePasswordRequest
import com.pinakes.app.data.model.DeviceItem
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.ForgotRequest
import com.pinakes.app.data.model.GenreNode
import com.pinakes.app.data.model.HealthPayload
import com.pinakes.app.data.model.LoanItem
import com.pinakes.app.data.model.LoansData
import com.pinakes.app.data.model.LoginRequest
import com.pinakes.app.data.model.LoginResponse
import com.pinakes.app.data.model.MessageRequest
import com.pinakes.app.data.model.MyReview
import com.pinakes.app.data.model.NotificationItem
import com.pinakes.app.data.model.Review
import com.pinakes.app.data.model.ReviewRequest
import com.pinakes.app.data.model.PushPrefs
import com.pinakes.app.data.model.PushSubscribeRequest
import com.pinakes.app.data.model.RegisterRequest
import com.pinakes.app.data.model.ReservationItem
import com.pinakes.app.data.model.ReservationRequest
import com.pinakes.app.data.model.UpdateProfileRequest
import com.pinakes.app.data.model.UserProfile
import com.pinakes.app.data.model.WishlistAddRequest
import com.pinakes.app.data.model.WishlistItem
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Pinakes Mobile API (`/api/v1`). Base URL is the user-entered instance URL + `/api/v1/`.
 *
 * The [AuthInterceptor] injects `Authorization: Bearer <token>` on every call except the
 * public ones, which are tagged [NO_AUTH] so the interceptor skips them.
 */
interface PinakesApi {

    // ---- Public (no token) ----
    @GET("health")
    @Headers(NO_AUTH)
    suspend fun health(): Envelope<HealthPayload>

    @POST("auth/login")
    @Headers(NO_AUTH)
    suspend fun login(@Body body: LoginRequest): Envelope<LoginResponse>

    @POST("auth/register")
    @Headers(NO_AUTH)
    suspend fun register(@Body body: RegisterRequest): Envelope<Unit>

    @POST("auth/forgot-password")
    @Headers(NO_AUTH)
    suspend fun forgotPassword(@Body body: ForgotRequest): Envelope<Unit>

    // ---- Auth / devices ----
    @POST("auth/logout")
    suspend fun logout(): Envelope<Unit>

    @GET("me/devices")
    suspend fun devices(): Envelope<List<DeviceItem>>

    @DELETE("me/devices/{id}")
    suspend fun revokeDevice(@Path("id") id: Int): Envelope<Unit>

    // ---- Profile ----
    @GET("me")
    suspend fun me(): Envelope<UserProfile>

    @PATCH("me")
    suspend fun updateMe(@Body body: UpdateProfileRequest): Envelope<UserProfile>

    @POST("me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Envelope<Unit>

    // ---- Catalog ----
    @GET("catalog/search")
    suspend fun search(
        @Query("q") q: String? = null,
        @Query("author") author: String? = null,
        @Query("publisher") publisher: String? = null,
        @Query("genre") genre: Int? = null,
        @Query("language") language: String? = null,
        @Query("available") available: Boolean? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null, // 1..50, default 20
        // newest (default) | oldest | title_asc | title_desc
        @Query("sort") sort: String? = null,
    ): Envelope<List<BookSummary>>

    @GET("catalog/books/{id}")
    suspend fun book(
        @Path("id") id: Int,
        @Header("If-None-Match") etag: String? = null, // -> 304 cache
    ): Response<Envelope<BookDetail>> // Response<> to read ETag header + 304

    @GET("catalog/books/{id}/availability")
    suspend fun bookAvailability(@Path("id") id: Int): Envelope<AvailabilityCalendar>

    @GET("catalog/genres")
    suspend fun genres(): Envelope<List<GenreNode>>

    // ---- Reviews ----
    /** Aggregate rating + the user's own review + a page of other users' reviews for a book. */
    @GET("catalog/books/{id}/reviews")
    suspend fun bookReviews(
        @Path("id") id: Int,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null, // 1..50, default 20
    ): Envelope<BookReviews>

    /** Upsert the current user's review for a book (create or edit). Requires a past loan. */
    @PUT("catalog/books/{id}/reviews")
    suspend fun submitReview(
        @Path("id") id: Int,
        @Body body: ReviewRequest,
    ): Envelope<Review>

    /** Delete the current user's review for a book (idempotent). */
    @DELETE("catalog/books/{id}/reviews")
    suspend fun deleteReview(@Path("id") id: Int): Envelope<Unit>

    /** The current user's own reviews across all books (for the "My reviews" page). */
    @GET("me/reviews")
    suspend fun myReviews(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null, // 1..50, default 20
    ): Envelope<List<MyReview>>

    // ---- Loans / reservations ----
    @GET("me/loans")
    suspend fun loans(): Envelope<LoansData>

    @GET("me/reservations")
    suspend fun reservations(): Envelope<List<ReservationItem>>

    @POST("reservations")
    suspend fun createReservation(@Body body: ReservationRequest): Envelope<Unit>

    @DELETE("reservations/{id}")
    suspend fun cancelReservation(@Path("id") id: Int): Envelope<Unit>

    // ---- Wishlist ----
    @GET("me/wishlist")
    suspend fun wishlist(): Envelope<List<WishlistItem>>

    @POST("me/wishlist")
    suspend fun addWishlist(@Body body: WishlistAddRequest): Envelope<Unit>

    @DELETE("me/wishlist/{book_id}")
    suspend fun removeWishlist(@Path("book_id") bookId: Int): Envelope<Unit>

    // ---- Messaging / notifications ----
    @POST("messages")
    suspend fun sendMessage(@Body body: MessageRequest): Envelope<Unit>

    @GET("me/notifications")
    suspend fun notifications(): Envelope<List<NotificationItem>>

    // ---- Push ----
    @POST("me/push/subscribe")
    suspend fun pushSubscribe(@Body body: PushSubscribeRequest): Envelope<Unit>

    @DELETE("me/push/subscribe")
    suspend fun pushUnsubscribe(): Envelope<Unit>

    @GET("me/push/prefs")
    suspend fun pushPrefs(): Envelope<PushPrefs>

    @PUT("me/push/prefs")
    suspend fun setPushPrefs(@Body body: PushPrefs): Envelope<Unit>

    companion object {
        /** Header marker (value-less) the [AuthInterceptor] uses to skip bearer injection. */
        const val NO_AUTH = "X-Pinakes-No-Auth: true"
    }
}
