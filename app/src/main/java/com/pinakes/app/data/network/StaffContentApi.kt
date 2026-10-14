package com.pinakes.app.data.network

import com.pinakes.app.data.model.AssignResult
import com.pinakes.app.data.model.BookIdsRequest
import com.pinakes.app.data.model.ChunkRequest
import com.pinakes.app.data.model.CopyUpdate
import com.pinakes.app.data.model.CronRequest
import com.pinakes.app.data.model.CronResult
import com.pinakes.app.data.model.DeleteResult
import com.pinakes.app.data.model.DuplicateAuthor
import com.pinakes.app.data.model.EnrichBatchResult
import com.pinakes.app.data.model.EnrichOverview
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.FetchCoverResult
import com.pinakes.app.data.model.GenreMergeResult
import com.pinakes.app.data.model.ImportJob
import com.pinakes.app.data.model.ImportsOverview
import com.pinakes.app.data.model.LabelSettings
import com.pinakes.app.data.model.MergeRequest
import com.pinakes.app.data.model.MergeResult
import com.pinakes.app.data.model.MissingCover
import com.pinakes.app.data.model.ParentWorkRequest
import com.pinakes.app.data.model.ParentWorkResult
import com.pinakes.app.data.model.SeriesDeleteResult
import com.pinakes.app.data.model.SeriesMergeResult
import com.pinakes.app.data.model.SeriesRenameRequest
import com.pinakes.app.data.model.StaffCopy
import com.pinakes.app.data.model.StaffSeries
import com.pinakes.app.data.model.StaffSeriesDetail
import com.pinakes.app.data.model.SyncResult
import com.pinakes.app.data.model.TargetRequest
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Staff content (Mobile API 1.7.0): copies, series, merges, labels, imports, covers and
 * enrichment. [download] fetches a PDF or CSV with the bearer token (labels, receipts,
 * import errors): those never go through the unauthenticated media client.
 */
interface StaffContentApi {

    @GET("staff/copies/{id}")
    suspend fun copy(@Path("id") id: Int): Envelope<StaffCopy>

    @PUT("staff/copies/{id}")
    suspend fun updateCopy(@Path("id") id: Int, @Body body: CopyUpdate): Envelope<StaffCopy>

    @DELETE("staff/copies/{id}")
    suspend fun deleteCopy(@Path("id") id: Int): Envelope<DeleteResult>

    @GET("staff/series")
    suspend fun series(@Query("q") q: String? = null, @Query("flat") flat: Int? = null): Envelope<List<StaffSeries>>

    @GET("staff/series/{id}")
    suspend fun seriesDetail(@Path("id") id: Int): Envelope<StaffSeriesDetail>

    @GET("staff/series/suggest")
    suspend fun seriesSuggest(@Query("q") q: String, @Query("field") field: String? = null): Envelope<List<String>>

    @POST("staff/series")
    suspend fun createSeries(@Body body: JsonObject): Envelope<StaffSeriesDetail>

    @PUT("staff/series/{id}")
    suspend fun updateSeries(@Path("id") id: Int, @Body body: JsonObject): Envelope<StaffSeriesDetail>

    @POST("staff/series/{id}/rename")
    suspend fun renameSeries(@Path("id") id: Int, @Body body: SeriesRenameRequest): Envelope<StaffSeriesDetail>

    @POST("staff/series/{id}/merge")
    suspend fun mergeSeries(@Path("id") id: Int, @Body body: TargetRequest): Envelope<SeriesMergeResult>

    @DELETE("staff/series/{id}")
    suspend fun deleteSeries(@Path("id") id: Int): Envelope<SeriesDeleteResult>

    @POST("staff/series/{id}/books")
    suspend fun assignBooks(@Path("id") id: Int, @Body body: BookIdsRequest): Envelope<AssignResult>

    /** Body `{series_number: string|null}`, built raw so a cleared number is sent as JSON null. */
    @PUT("staff/series/{id}/books/{bookId}")
    suspend fun setSeriesNumber(@Path("id") id: Int, @Path("bookId") bookId: Int, @Body body: JsonObject): Envelope<StaffSeriesDetail>

    @DELETE("staff/series/{id}/books/{bookId}")
    suspend fun removeFromSeries(@Path("id") id: Int, @Path("bookId") bookId: Int): Envelope<StaffSeriesDetail>

    @POST("staff/series/{id}/parent-work")
    suspend fun parentWork(@Path("id") id: Int, @Body body: ParentWorkRequest): Envelope<ParentWorkResult>

    @GET("staff/authors/duplicates")
    suspend fun authorDuplicates(): Envelope<List<List<DuplicateAuthor>>>

    @POST("staff/authors/merge")
    suspend fun mergeAuthors(@Body body: MergeRequest): Envelope<MergeResult>

    @POST("staff/publishers/merge")
    suspend fun mergePublishers(@Body body: MergeRequest): Envelope<MergeResult>

    @POST("staff/genres/{id}/merge")
    suspend fun mergeGenre(@Path("id") id: Int, @Body body: TargetRequest): Envelope<GenreMergeResult>

    @GET("staff/settings/labels")
    suspend fun labelSettings(): Envelope<LabelSettings>

    @PUT("staff/settings/labels")
    suspend fun saveLabelSettings(@Body body: LabelSettings): Envelope<LabelSettings>

    @Multipart
    @POST("staff/imports")
    suspend fun createImport(
        @Part file: MultipartBody.Part,
        @Part("kind") kind: RequestBody,
        @Part("enable_scraping") enableScraping: RequestBody,
        @Part("update_fields") updateFields: RequestBody?,
    ): Envelope<ImportJob>

    @POST("staff/imports/{id}/chunk")
    suspend fun importChunk(@Path("id") id: String, @Body body: ChunkRequest): Envelope<ImportJob>

    @GET("staff/imports/{id}")
    suspend fun importJob(@Path("id") id: String): Envelope<ImportJob>

    @DELETE("staff/imports/{id}")
    suspend fun cancelImport(@Path("id") id: String): Envelope<JsonObject>

    @GET("staff/imports")
    suspend fun imports(@Query("cursor") cursor: String? = null, @Query("limit") limit: Int? = null): Envelope<ImportsOverview>

    @GET("staff/covers/missing")
    suspend fun missingCovers(@Query("cursor") cursor: String? = null, @Query("limit") limit: Int? = null): Envelope<List<MissingCover>>

    @POST("staff/books/{id}/cover/fetch")
    suspend fun fetchCover(@Path("id") bookId: Int): Envelope<FetchCoverResult>

    @POST("staff/covers/sync")
    suspend fun syncCovers(): Envelope<SyncResult>

    @GET("staff/enrich")
    suspend fun enrich(): Envelope<EnrichOverview>

    @POST("staff/enrich/batch")
    suspend fun enrichBatch(): Envelope<EnrichBatchResult>

    @PUT("staff/enrich/cron")
    suspend fun enrichCron(@Body body: CronRequest): Envelope<CronResult>

    @Multipart
    @POST("staff/books/{id}/digital")
    suspend fun uploadDigital(
        @Path("id") bookId: Int,
        @Part file: MultipartBody.Part,
        @Part("digital_type") digitalType: RequestBody,
    ): Envelope<com.pinakes.app.data.model.DigitalUploadResult>

    /** A binary staff export (PDF, CSV), relative to the API base URL. */
    @Streaming
    @GET
    suspend fun download(@Url path: String): Response<ResponseBody>
}
