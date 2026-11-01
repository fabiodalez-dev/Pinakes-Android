package com.pinakes.app.data.repository

import com.pinakes.app.data.model.ChunkRequest
import com.pinakes.app.data.model.CopyCatalogingImport
import com.pinakes.app.data.model.CopyCatalogingImportRequest
import com.pinakes.app.data.model.CopyCatalogingSearch
import com.pinakes.app.data.model.CopyCatalogingSearchRequest
import com.pinakes.app.data.model.CopyCatalogingServers
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.Envelope090
import com.pinakes.app.data.model.MarcCancelResult
import com.pinakes.app.data.model.MarcImportJob
import com.pinakes.app.data.model.MarcOptions
import com.pinakes.app.data.model.Meta
import com.pinakes.app.data.model.Supplier
import com.pinakes.app.data.model.SupplierDeleteResult
import com.pinakes.app.data.model.SupplierList
import com.pinakes.app.data.model.fieldErrors
import com.pinakes.app.data.model.metaBoolean
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.Staff090CatalogueApi
import com.pinakes.app.data.store.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** A write that may fail field by field: the result and the 422's `meta.fields`. */
data class FieldsResult<T>(val result: ApiResult<T>, val fields: Map<String, String> = emptyMap())

/** The choices of a MARC upload (the server's names). */
data class MarcUploadOptions(
    val flavour: String = "auto",
    val encoding: String = "auto",
    val duplicates: String = "skip",
    val downloadCovers: Boolean = false,
    /** Only while the suppliers module is on. */
    val supplierId: Int? = null,
)

/**
 * Pinakes 0.9.0 cataloguing (Mobile API 1.10.0): suppliers, MARC import, copy cataloguing.
 * Every call goes through [staffCall] (a 403 `forbidden` drops the role). The envelope's meta
 * is read raw, because these routes answer keys the shared [Meta] does not carry. Uploads,
 * chunks and catalogue searches use the long-timeout client.
 */
@Singleton
class Staff090CatalogueRepository @Inject constructor(
    private val network: NetworkModule,
    private val session: SessionStore,
) {
    private val api get() = network.typedApi(Staff090CatalogueApi::class.java)
    private val longApi get() = network.longTypedApi(Staff090CatalogueApi::class.java)

    // ---------- suppliers ----------

    suspend fun suppliers(query: String? = null): ApiResult<SupplierList> {
        val (res, meta) = call { api.suppliers(query.blankToNull()) }
        return res.map { SupplierList(it, meta.metaBoolean("can_create") ?: false) }
    }

    suspend fun supplier(id: Int): ApiResult<Supplier> = call { api.supplier(id) }.first

    /** Create (with [idempotencyKey]) when [id] is null, else update with [body] as the patch. */
    suspend fun saveSupplier(id: Int?, body: JsonObject, idempotencyKey: String): FieldsResult<Supplier> {
        val (res, meta) = call { if (id == null) api.createSupplier(body, idempotencyKey) else api.updateSupplier(id, body) }
        return FieldsResult(res, if (res is ApiResult.Failure) meta.fieldErrors() else emptyMap())
    }

    suspend fun deleteSupplier(id: Int): ApiResult<SupplierDeleteResult> = call { api.deleteSupplier(id) }.first

    // ---------- MARC ----------

    suspend fun marcOptions(): ApiResult<MarcOptions> = call { api.marcOptions() }.first

    suspend fun createMarcImport(fileName: String, bytes: ByteArray, options: MarcUploadOptions): ApiResult<MarcImportJob> {
        val text = "text/plain".toMediaType()
        val part = MultipartBody.Part.createFormData("file", fileName, bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
        return call {
            longApi.createMarcImport(
                part,
                options.flavour.toRequestBody(text),
                options.encoding.toRequestBody(text),
                options.duplicates.toRequestBody(text),
                (if (options.downloadCovers) "1" else "0").toRequestBody(text),
                options.supplierId?.takeIf { it > 0 }?.toString()?.toRequestBody(text),
            )
        }.first
    }

    /** One page of the caller's MARC jobs (finished ones included: the screen keeps the open ones). */
    suspend fun marcImports(cursor: String? = null, limit: Int = 30): ApiResult<StaffPage<MarcImportJob>> =
        call { api.marcImports(limit, cursor) }.first.toPage()

    suspend fun marcImport(id: String): ApiResult<MarcImportJob> = call { api.marcImport(id) }.first

    /** One chunk from [start]; with cover downloads the server may need minutes. */
    suspend fun marcImportChunk(id: String, start: Int): ApiResult<MarcImportJob> =
        call { longApi.marcImportChunk(id, ChunkRequest(start = start)) }.first

    suspend fun cancelMarcImport(id: String): ApiResult<MarcCancelResult> = call { api.cancelMarcImport(id) }.first

    // ---------- copy cataloguing ----------

    suspend fun copyCatalogingServers(): ApiResult<CopyCatalogingServers> = call { api.copyCatalogingServers() }.first

    suspend fun copyCatalogingSearch(request: CopyCatalogingSearchRequest): ApiResult<CopyCatalogingSearch> =
        call { longApi.copyCatalogingSearch(request) }.first

    suspend fun copyCatalogingImport(request: CopyCatalogingImportRequest, idempotencyKey: String): ApiResult<CopyCatalogingImport> =
        call { longApi.copyCatalogingImport(request, idempotencyKey) }.first

    // ---------- plumbing ----------

    /**
     * [staffCall] on an [Envelope090]: the meta is kept raw (success or error body) and also
     * decoded into the shared [Meta], so `processed`, `replayed` and the rest work as usual.
     */
    private suspend fun <T> call(block: suspend () -> Envelope090<T>): Pair<ApiResult<T>, JsonElement?> {
        var raw: JsonElement? = null
        val res = session.staffCall {
            val envelope = try {
                block()
            } catch (e: HttpException) {
                val response = e.response() ?: throw e
                val text = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
                raw = metaOf(network.json, text)
                // The body was read here: hand an identical one on to the shared parser.
                throw HttpException(Response.error<Any>(text.toResponseBody(response.errorBody()?.contentType()), response.raw()))
            }
            raw = envelope.meta
            Envelope(data = envelope.data, meta = envelope.meta?.let { m -> runCatching { network.json.decodeFromJsonElement(Meta.serializer(), m) }.getOrNull() }, error = envelope.error)
        }
        return res to raw
    }

    companion object {
        /** The `meta` of an error body, or null when it is not the API's envelope. */
        fun metaOf(json: Json, body: String): JsonElement? =
            runCatching { (json.parseToJsonElement(body) as? JsonObject)?.get("meta") }.getOrNull()
    }
}

/** Download paths of the cataloguing PDFs and exports (relative to `/api/v1/`). Pure. */
object Staff090CataloguePaths {
    /** Web rule: at most 200 cards in one PDF. */
    const val MAX_CARDS = 200

    /** Server rule: at most 1000 ids in one MARC export. */
    const val MAX_EXPORT_IDS = 1000

    fun catalogueCard(bookId: Int): String = "staff/books/$bookId/catalogue-card.pdf"

    /** Distinct positive ids in the order given; null when none is left. */
    fun catalogueCards(ids: List<Int>): String? =
        ids.filter { it > 0 }.distinct().take(MAX_CARDS).takeIf { it.isNotEmpty() }?.let { "staff/books/catalogue-cards.pdf?ids=${it.joinToString(",")}" }

    /** The whole catalogue when [ids] is null; a selection otherwise (null when empty or over the limit). */
    fun marcExport(format: String, ids: List<Int>?): String? {
        val safeFormat = format.takeIf { it in FORMATS } ?: "marc21"
        if (ids == null) return "staff/marc/export?format=$safeFormat"
        val list = ids.filter { it > 0 }.distinct()
        if (list.isEmpty() || list.size > MAX_EXPORT_IDS) return null
        return "staff/marc/export?format=$safeFormat&ids=${list.joinToString(",")}"
    }

    /** Mime of an export for the viewer/share sheet. */
    fun exportMime(format: String): String = if (format == "marcxml") "application/marcxml+xml" else "application/marc"

    /** The file name used when the server sends none. */
    fun exportFallbackName(format: String): String = if (format == "marcxml") "catalogo_marcxml.xml" else "catalogo_$format.mrc"

    val FORMATS = listOf("marc21", "marcxml", "unimarc")
}
