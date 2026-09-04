package com.pinakes.app.data.network

import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.PeriodicalDetail
import com.pinakes.app.data.model.PeriodicalIssue
import com.pinakes.app.data.model.PeriodicalIssueDetail
import com.pinakes.app.data.model.PeriodicalSummary
import com.pinakes.app.data.model.PeriodicalsHealth
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Periodicals ("Emeroteca") plugin endpoints, exposed under `/api/v1/periodicals/…`.
 * Base URL is the same instance origin + `/api/v1/` used by [PinakesApi], and every call
 * carries the SAME bearer token (injected by [AuthInterceptor]) — the app authenticates
 * once via the core Mobile API.
 *
 * Unlike [BookClubApi], these responses use the CORE `{data, meta, error}` envelope, so they
 * are wrapped with the shared `apiCall`. The whole surface is read-only.
 */
interface PeriodicalsApi {

    /** Discovery — 2xx means the section is available; 404 means the plugin is off. */
    @GET("periodicals/health")
    suspend fun health(): Envelope<PeriodicalsHealth>

    /** Mastheads list; cursor-paginated like the catalog search (`meta.next_cursor`). */
    @GET("periodicals")
    suspend fun periodicals(
        @Query("q") q: String? = null,
        @Query("type") type: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Envelope<List<PeriodicalSummary>>

    /** Masthead detail + its years (annate). */
    @GET("periodicals/{id}")
    suspend fun periodical(@Path("id") id: Int): Envelope<PeriodicalDetail>

    /** Issues (fascicoli) of a year. */
    @GET("periodicals/years/{id}/issues")
    suspend fun yearIssues(@Path("id") yearId: Int): Envelope<List<PeriodicalIssue>>

    /** Issue detail + spoglio articles + public PDF url when available. */
    @GET("periodicals/issues/{id}")
    suspend fun issue(@Path("id") id: Int): Envelope<PeriodicalIssueDetail>
}
