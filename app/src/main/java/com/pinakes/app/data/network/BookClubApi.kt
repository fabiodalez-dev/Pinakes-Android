package com.pinakes.app.data.network

import com.pinakes.app.data.model.BookClubClubs
import com.pinakes.app.data.model.BookClubDashboard
import com.pinakes.app.data.model.BookClubDetail
import com.pinakes.app.data.model.BookClubEnvelope
import com.pinakes.app.data.model.BookClubHealth
import com.pinakes.app.data.model.ClubProgressRequest
import com.pinakes.app.data.model.ClubProposalRequest
import com.pinakes.app.data.model.ClubRsvpRequest
import com.pinakes.app.data.model.ClubVoteRequest
import com.pinakes.app.data.model.JoinResult
import com.pinakes.app.data.model.ProgressResult
import com.pinakes.app.data.model.RsvpResult
import com.pinakes.app.data.model.VoteResult
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Book Club plugin endpoints, exposed by the plugin's `mobile` module under
 * `/api/v1/bookclub/…`. Base URL is the same instance origin + `/api/v1/` used by
 * [PinakesApi], and the authenticated calls carry the SAME bearer token (injected by
 * [AuthInterceptor]) — the app authenticates once via the core Mobile API.
 *
 * These responses use the Book Club envelope ([BookClubEnvelope]: `{success, data, error}`),
 * NOT the core `{data, meta, error}` one — so they are wrapped with `bookClubCall`, not `apiCall`.
 */
interface BookClubApi {

    /** Discovery — no token. 2xx means the section is available; 404 means the plugin is off. */
    @GET("bookclub/health")
    @Headers(NO_AUTH)
    suspend fun health(): BookClubEnvelope<BookClubHealth>

    // ---- Reads ----
    @GET("bookclub/clubs")
    suspend fun clubs(): BookClubEnvelope<BookClubClubs>

    @GET("bookclub/clubs/{slug}")
    suspend fun clubDetail(@Path("slug") slug: String): BookClubEnvelope<BookClubDetail>

    @GET("bookclub/me/dashboard")
    suspend fun dashboard(): BookClubEnvelope<BookClubDashboard>

    // ---- Actions (mirror the web rules server-side) ----
    @POST("bookclub/clubs/{slug}/join")
    suspend fun join(@Path("slug") slug: String): BookClubEnvelope<JoinResult>

    @POST("bookclub/clubs/{slug}/proposals")
    suspend fun propose(
        @Path("slug") slug: String,
        @Body body: ClubProposalRequest,
    ): BookClubEnvelope<Unit>

    @POST("bookclub/clubs/{slug}/polls/{pollId}/vote")
    suspend fun vote(
        @Path("slug") slug: String,
        @Path("pollId") pollId: Int,
        @Body body: ClubVoteRequest,
    ): BookClubEnvelope<VoteResult>

    @POST("bookclub/clubs/{slug}/meetings/{meetingId}/rsvp")
    suspend fun rsvp(
        @Path("slug") slug: String,
        @Path("meetingId") meetingId: Int,
        @Body body: ClubRsvpRequest,
    ): BookClubEnvelope<RsvpResult>

    @POST("bookclub/clubs/{slug}/books/{clubBookId}/progress")
    suspend fun progress(
        @Path("slug") slug: String,
        @Path("clubBookId") clubBookId: Int,
        @Body body: ClubProgressRequest,
    ): BookClubEnvelope<ProgressResult>

    companion object {
        /** Same value-less marker [AuthInterceptor] uses to skip bearer injection on public calls. */
        const val NO_AUTH = "X-Pinakes-No-Auth: true"
    }
}
