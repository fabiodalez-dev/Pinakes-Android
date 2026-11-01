package com.pinakes.app.data.network

import com.pinakes.app.data.model.ChunkRequest
import com.pinakes.app.data.model.CopyCatalogingImport
import com.pinakes.app.data.model.CopyCatalogingImportRequest
import com.pinakes.app.data.model.CopyCatalogingSearch
import com.pinakes.app.data.model.CopyCatalogingSearchRequest
import com.pinakes.app.data.model.CopyCatalogingServers
import com.pinakes.app.data.model.Envelope090
import com.pinakes.app.data.model.MarcCancelResult
import com.pinakes.app.data.model.MarcImportJob
import com.pinakes.app.data.model.MarcOptions
import com.pinakes.app.data.model.Supplier
import com.pinakes.app.data.model.SupplierDeleteResult
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Pinakes 0.9.0 cataloguing areas (Mobile API 1.10.0): suppliers (module `suppliers`), MARC
 * import/export and copy cataloguing (module `marc`, z39-server plugin). Catalogue card PDFs and
 * the MARC export are binary downloads through [com.pinakes.app.data.repository.StaffFilesRepository].
 */
interface Staff090CatalogueApi {

    // ---------- Suppliers ----------

    @GET("staff/suppliers")
    suspend fun suppliers(@Query("q") q: String? = null): Envelope090<List<Supplier>>

    @GET("staff/suppliers/{id}")
    suspend fun supplier(@Path("id") id: Int): Envelope090<Supplier>

    @POST("staff/suppliers")
    suspend fun createSupplier(@Body body: JsonObject, @Header("Idempotency-Key") key: String): Envelope090<Supplier>

    @PUT("staff/suppliers/{id}")
    suspend fun updateSupplier(@Path("id") id: Int, @Body body: JsonObject): Envelope090<Supplier>

    @DELETE("staff/suppliers/{id}")
    suspend fun deleteSupplier(@Path("id") id: Int): Envelope090<SupplierDeleteResult>

    // ---------- MARC ----------

    @GET("staff/marc/options")
    suspend fun marcOptions(): Envelope090<MarcOptions>

    /** Upload on the long-timeout client: the server stages every record before answering. */
    @Multipart
    @POST("staff/marc/imports")
    suspend fun createMarcImport(
        @Part file: MultipartBody.Part,
        @Part("flavour") flavour: RequestBody,
        @Part("encoding") encoding: RequestBody,
        @Part("duplicates") duplicates: RequestBody,
        @Part("download_covers") downloadCovers: RequestBody,
        @Part("supplier_id") supplierId: RequestBody?,
    ): Envelope090<MarcImportJob>

    /** The caller's unexpired MARC jobs, newest first (every status), paged like `/staff/imports`. */
    @GET("staff/marc/imports")
    suspend fun marcImports(@Query("limit") limit: Int? = null, @Query("cursor") cursor: String? = null): Envelope090<List<MarcImportJob>>

    @GET("staff/marc/imports/{id}")
    suspend fun marcImport(@Path("id") id: String): Envelope090<MarcImportJob>

    @POST("staff/marc/imports/{id}/chunk")
    suspend fun marcImportChunk(@Path("id") id: String, @Body body: ChunkRequest): Envelope090<MarcImportJob>

    @DELETE("staff/marc/imports/{id}")
    suspend fun cancelMarcImport(@Path("id") id: String): Envelope090<MarcCancelResult>

    // ---------- Copy cataloguing ----------

    @GET("staff/copy-cataloging/servers")
    suspend fun copyCatalogingServers(): Envelope090<CopyCatalogingServers>

    /** Each catalogue may take seconds to answer: long-timeout client. */
    @POST("staff/copy-cataloging/search")
    suspend fun copyCatalogingSearch(@Body body: CopyCatalogingSearchRequest): Envelope090<CopyCatalogingSearch>

    @POST("staff/copy-cataloging/import")
    suspend fun copyCatalogingImport(
        @Body body: CopyCatalogingImportRequest,
        @Header("Idempotency-Key") key: String,
    ): Envelope090<CopyCatalogingImport>
}
