package com.pinakes.app.data.network

import com.pinakes.app.data.model.ArchiveLookups
import com.pinakes.app.data.model.AttachAuthorityRequest
import com.pinakes.app.data.model.AuthorityDetail
import com.pinakes.app.data.model.AuthorityRow
import com.pinakes.app.data.model.DeleteResult
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.ReceiveOfferRequest
import com.pinakes.app.data.model.StaffArchiveDetail
import com.pinakes.app.data.model.StaffArchiveRow
import com.pinakes.app.data.model.StaffOffer
import com.pinakes.app.data.model.StaffWantedBook
import com.pinakes.app.data.model.WantedReceiveResult
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Staff management of plugin content (Mobile API 1.7.0): Desiderata and Archives. A plugin
 * that is not active answers 404 `feature_disabled`; the app hides the entry from `/health`.
 */
interface StaffPluginsApi {

    @GET("staff/desiderata/offers")
    suspend fun offers(
        @Query("status") status: String? = null,
        @Query("q") q: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Envelope<List<StaffOffer>>

    @POST("staff/desiderata/offers/{id}/accept")
    suspend fun acceptOffer(@Path("id") id: Int): Envelope<StaffOffer>

    @POST("staff/desiderata/offers/{id}/reject")
    suspend fun rejectOffer(@Path("id") id: Int): Envelope<StaffOffer>

    @POST("staff/desiderata/offers/{id}/receive")
    suspend fun receiveOffer(@Path("id") id: Int, @Body body: ReceiveOfferRequest): Envelope<StaffOffer>

    @DELETE("staff/desiderata/offers/{id}")
    suspend fun deleteOffer(@Path("id") id: Int): Envelope<DeleteResult>

    @GET("staff/desiderata/wanted")
    suspend fun wanted(
        @Query("q") q: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Envelope<List<StaffWantedBook>>

    @POST("staff/desiderata/wanted/{bookId}/receive")
    suspend fun receiveWanted(@Path("bookId") bookId: Int): Envelope<WantedReceiveResult>

    @GET("staff/archives/lookups")
    suspend fun archiveLookups(): Envelope<ArchiveLookups>

    @GET("staff/archives")
    suspend fun archives(
        @Query("q") q: String? = null,
        @Query("level") level: String? = null,
        @Query("published") published: String? = null,
        @Query("parent_id") parentId: Int? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Envelope<List<StaffArchiveRow>>

    @GET("staff/archives/{id}")
    suspend fun archive(@Path("id") id: Int): Envelope<StaffArchiveDetail>

    @POST("staff/archives")
    suspend fun createArchive(@Body body: JsonObject): Envelope<StaffArchiveDetail>

    @PUT("staff/archives/{id}")
    suspend fun updateArchive(@Path("id") id: Int, @Body body: JsonObject): Envelope<StaffArchiveDetail>

    @DELETE("staff/archives/{id}")
    suspend fun deleteArchive(@Path("id") id: Int): Envelope<DeleteResult>

    @Multipart
    @POST("staff/archives/{id}/cover")
    suspend fun uploadArchiveCover(@Path("id") id: Int, @Part cover: MultipartBody.Part): Envelope<StaffArchiveDetail>

    @DELETE("staff/archives/{id}/cover")
    suspend fun deleteArchiveCover(@Path("id") id: Int): Envelope<StaffArchiveDetail>

    @Multipart
    @POST("staff/archives/{id}/documents")
    suspend fun uploadArchiveDocument(@Path("id") id: Int, @Part document: MultipartBody.Part): Envelope<StaffArchiveDetail>

    @DELETE("staff/archives/{id}/documents/{fileId}")
    suspend fun deleteArchiveDocument(@Path("id") id: Int, @Path("fileId") fileId: Int): Envelope<StaffArchiveDetail>

    @POST("staff/archives/{id}/authorities")
    suspend fun attachAuthority(@Path("id") id: Int, @Body body: AttachAuthorityRequest): Envelope<StaffArchiveDetail>

    @DELETE("staff/archives/{id}/authorities/{authorityId}")
    suspend fun detachAuthority(@Path("id") id: Int, @Path("authorityId") authorityId: Int): Envelope<StaffArchiveDetail>

    @GET("staff/archives/authorities")
    suspend fun authorities(
        @Query("q") q: String? = null,
        @Query("type") type: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Envelope<List<AuthorityRow>>

    @GET("staff/archives/authorities/{id}")
    suspend fun authority(@Path("id") id: Int): Envelope<AuthorityDetail>

    @POST("staff/archives/authorities")
    suspend fun createAuthority(@Body body: JsonObject): Envelope<AuthorityDetail>

    @PUT("staff/archives/authorities/{id}")
    suspend fun updateAuthority(@Path("id") id: Int, @Body body: JsonObject): Envelope<AuthorityDetail>

    @DELETE("staff/archives/authorities/{id}")
    suspend fun deleteAuthority(@Path("id") id: Int): Envelope<DeleteResult>
}
