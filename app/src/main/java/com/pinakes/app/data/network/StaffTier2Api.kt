package com.pinakes.app.data.network

import com.pinakes.app.data.model.AddMemberRequest
import com.pinakes.app.data.model.BookWorkLink
import com.pinakes.app.data.model.ClubMember
import com.pinakes.app.data.model.DeleteResult
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.IssueWrite
import com.pinakes.app.data.model.MemberUpdate
import com.pinakes.app.data.model.PeriodicalScan
import com.pinakes.app.data.model.StaffClub
import com.pinakes.app.data.model.StaffIssue
import com.pinakes.app.data.model.StaffSubscription
import com.pinakes.app.data.model.StaffWork
import com.pinakes.app.data.model.StaffYear
import com.pinakes.app.data.model.WorkExpression
import com.pinakes.app.data.model.YearWrite
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Tier 2 staff bridges (Mobile API 1.7.0), each mounted only with its plugin: the periodicals
 * desk, book club management and FRBR works. Same base URL, token and envelope as [PinakesApi].
 */
interface StaffTier2Api {

    // ---- periodicals desk (emeroteca) ----

    @GET("periodicals/staff/scan-lookup")
    suspend fun scanLookup(@Query("code") code: String, @Query("periodical_id") periodicalId: Int? = null): Envelope<PeriodicalScan>

    @POST("periodicals/staff/issues/{id}/receive")
    suspend fun receiveIssue(@Path("id") id: Int): Envelope<StaffIssue>

    @POST("periodicals/staff/issues/{id}/claim")
    suspend fun claimIssue(@Path("id") id: Int): Envelope<StaffIssue>

    @POST("periodicals/staff/years/{id}/issues")
    suspend fun addIssue(@Path("id") yearId: Int, @Body body: IssueWrite): Envelope<StaffIssue>

    @POST("periodicals/staff/{id}/years")
    suspend fun addYear(@Path("id") periodicalId: Int, @Body body: YearWrite): Envelope<StaffYear>

    @GET("periodicals/staff/subscriptions")
    suspend fun subscriptions(@Query("periodical_id") periodicalId: Int? = null): Envelope<List<StaffSubscription>>

    @POST("periodicals/staff/subscriptions")
    suspend fun createSubscription(@Body body: JsonObject): Envelope<StaffSubscription>

    @PUT("periodicals/staff/subscriptions/{id}")
    suspend fun updateSubscription(@Path("id") id: Int, @Body body: JsonObject): Envelope<StaffSubscription>

    // ---- book clubs ----

    @GET("bookclub/staff/clubs")
    suspend fun clubs(): Envelope<List<StaffClub>>

    @GET("bookclub/staff/clubs/{id}")
    suspend fun club(@Path("id") id: Int): Envelope<StaffClub>

    @POST("bookclub/staff/clubs")
    suspend fun createClub(@Body body: JsonObject): Envelope<StaffClub>

    @PUT("bookclub/staff/clubs/{id}")
    suspend fun updateClub(@Path("id") id: Int, @Body body: JsonObject): Envelope<StaffClub>

    @DELETE("bookclub/staff/clubs/{id}")
    suspend fun deleteClub(@Path("id") id: Int): Envelope<DeleteResult>

    @PUT("bookclub/staff/clubs/{id}/workflow")
    suspend fun saveWorkflow(@Path("id") id: Int, @Body body: JsonObject): Envelope<StaffClub>

    @POST("bookclub/staff/clubs/{id}/members")
    suspend fun addMember(@Path("id") id: Int, @Body body: AddMemberRequest): Envelope<ClubMember>

    @PUT("bookclub/staff/clubs/{id}/members/{memberId}")
    suspend fun updateMember(@Path("id") id: Int, @Path("memberId") memberId: Int, @Body body: MemberUpdate): Envelope<ClubMember>

    // ---- FRBR works ----

    @GET("staff/works")
    suspend fun works(@Query("q") q: String? = null, @Query("limit") limit: Int? = null): Envelope<List<StaffWork>>

    @GET("staff/works/{id}")
    suspend fun work(@Path("id") id: Int): Envelope<StaffWork>

    @POST("staff/works")
    suspend fun createWork(@Body body: JsonObject): Envelope<StaffWork>

    @PUT("staff/works/{id}")
    suspend fun updateWork(@Path("id") id: Int, @Body body: JsonObject): Envelope<StaffWork>

    @DELETE("staff/works/{id}")
    suspend fun deleteWork(@Path("id") id: Int): Envelope<DeleteResult>

    @POST("staff/works/{id}/expressions")
    suspend fun createExpression(@Path("id") workId: Int, @Body body: JsonObject): Envelope<WorkExpression>

    @PUT("staff/works/expressions/{id}")
    suspend fun updateExpression(@Path("id") id: Int, @Body body: JsonObject): Envelope<WorkExpression>

    @DELETE("staff/works/expressions/{id}")
    suspend fun deleteExpression(@Path("id") id: Int): Envelope<DeleteResult>

    /** Body `{work_id: int|null, expression_id?: int|null}`; null detaches. */
    @POST("staff/books/{id}/work")
    suspend fun attachBook(@Path("id") bookId: Int, @Body body: JsonObject): Envelope<BookWorkLink>
}
