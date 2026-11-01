package com.pinakes.app.data.network

import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.Staff090StocktakeCloseRequest
import com.pinakes.app.data.model.Staff090StocktakeCreateRequest
import com.pinakes.app.data.model.Staff090StocktakeOptions
import com.pinakes.app.data.model.Staff090StocktakeReportRow
import com.pinakes.app.data.model.Staff090StocktakeScan
import com.pinakes.app.data.model.Staff090StocktakeScanRequest
import com.pinakes.app.data.model.Staff090StocktakeScanResult
import com.pinakes.app.data.model.Staff090StocktakeSession
import com.pinakes.app.data.model.Staff090StocktakeState
import com.pinakes.app.data.model.Staff090StocktakeUpload
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Inventory stocktake (Mobile API 1.10.0, `stocktake` module). Lists page with the shared
 * cursor (`meta.next_cursor`); the CSV and PDF reports go through [StaffContentApi.download].
 * Scans are rate limited at 600 a minute, as on the web scan page.
 */
interface Staff090StocktakeApi {

    @GET("staff/stocktake/options")
    suspend fun options(): Envelope<Staff090StocktakeOptions>

    /** [status]: `open` | `closed` | `cancelled` | `all`. */
    @GET("staff/stocktake/sessions")
    suspend fun sessions(
        @Query("status") status: String?,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Envelope<List<Staff090StocktakeSession>>

    /** [key]: one per submission; a replay answers 200 with `meta.replayed` and the same session. */
    @POST("staff/stocktake/sessions")
    suspend fun create(
        @Body body: Staff090StocktakeCreateRequest,
        @Header("Idempotency-Key") key: String? = null,
    ): Envelope<Staff090StocktakeSession>

    /** [mine] = 1 limits `recent_scans` to the signed-in operator. */
    @GET("staff/stocktake/sessions/{id}")
    suspend fun session(@Path("id") id: Int, @Query("mine") mine: Int? = null): Envelope<Staff090StocktakeSession>

    @GET("staff/stocktake/sessions/{id}/state")
    suspend fun state(@Path("id") id: Int, @Query("mine") mine: Int? = null): Envelope<Staff090StocktakeState>

    @GET("staff/stocktake/sessions/{id}/scans")
    suspend fun scans(
        @Path("id") id: Int,
        @Query("outcome") outcome: String? = null,
        @Query("mine") mine: Int? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Envelope<List<Staff090StocktakeScan>>

    /** 409 `session_closed` / `session_cancelled` once the session is no longer open. */
    @POST("staff/stocktake/sessions/{id}/scans")
    suspend fun scan(
        @Path("id") id: Int,
        @Body body: Staff090StocktakeScanRequest,
        @Header("Idempotency-Key") key: String? = null,
    ): Envelope<Staff090StocktakeScanResult>

    @GET("staff/stocktake/sessions/{id}/report")
    suspend fun report(
        @Path("id") id: Int,
        @Query("category") category: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Envelope<List<Staff090StocktakeReportRow>>

    /** A `.txt` / `.csv` list of codes (at most 2 MB), one scan each. */
    @Multipart
    @POST("staff/stocktake/sessions/{id}/upload")
    suspend fun upload(
        @Path("id") id: Int,
        @Part file: MultipartBody.Part,
        @Part("shelf_id") shelfId: RequestBody? = null,
    ): Envelope<Staff090StocktakeUpload>

    /** `lost` is refused to Staff with 403 `not_permitted`; posting it again on a mark-lost close finishes it. */
    @POST("staff/stocktake/sessions/{id}/close")
    suspend fun close(@Path("id") id: Int, @Body body: Staff090StocktakeCloseRequest): Envelope<Staff090StocktakeSession>

    @POST("staff/stocktake/sessions/{id}/cancel")
    suspend fun cancel(@Path("id") id: Int): Envelope<Staff090StocktakeSession>
}
