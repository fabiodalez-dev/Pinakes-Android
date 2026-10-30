package com.pinakes.app.data.repository

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
import com.pinakes.app.data.model.FetchCoverResult
import com.pinakes.app.data.model.GenreMergeResult
import com.pinakes.app.data.model.IdName
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
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.store.SessionStore
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** The import kinds of `POST /staff/imports`. */
enum class ImportKind(val wire: String) { CSV("csv"), LIBRARYTHING("librarything") }

/**
 * Staff content (Mobile API 1.7.0). Every call goes through [staffCall]. Long synchronous
 * server jobs (covers sync, an enrichment batch, an import chunk with scraping) use the
 * long-timeout client; everything else the normal one.
 */
class StaffContentRepository(
    private val network: NetworkModule,
    private val session: SessionStore,
) {
    private val api get() = network.staffContentApi()

    // ---------- copies ----------

    suspend fun copy(id: Int): ApiResult<StaffCopy> = session.staffCall { api.copy(id) }

    suspend fun updateCopy(id: Int, status: String?, notes: String?, consultationOnly: Boolean? = null): ApiResult<StaffCopy> =
        session.staffCall { api.updateCopy(id, CopyUpdate(status = status.blankToNull(), notes = notes?.trim(), consultationOnly = consultationOnly)) }

    suspend fun deleteCopy(id: Int): ApiResult<DeleteResult> = session.staffCall { api.deleteCopy(id) }

    // ---------- series ----------

    suspend fun series(query: String? = null, flat: Boolean = false): ApiResult<List<StaffSeries>> =
        session.staffCall { api.series(q = query.blankToNull(), flat = if (flat) 1 else null) }

    suspend fun seriesDetail(id: Int): ApiResult<StaffSeriesDetail> = session.staffCall { api.seriesDetail(id) }

    suspend fun seriesSuggest(query: String, field: String = "name"): ApiResult<List<String>> =
        session.staffCall { api.seriesSuggest(query.trim(), field) }

    suspend fun saveSeries(id: Int?, body: kotlinx.serialization.json.JsonObject): ApiResult<StaffSeriesDetail> =
        session.staffCall { if (id == null || id <= 0) api.createSeries(body) else api.updateSeries(id, body) }

    suspend fun renameSeries(id: Int, name: String, mergeIfExists: Boolean): ApiResult<StaffSeriesDetail> =
        session.staffCall { api.renameSeries(id, SeriesRenameRequest(name.trim(), mergeIfExists)) }

    suspend fun mergeSeries(id: Int, targetId: Int): ApiResult<SeriesMergeResult> = session.staffCall { api.mergeSeries(id, TargetRequest(targetId)) }

    suspend fun deleteSeries(id: Int): ApiResult<SeriesDeleteResult> = session.staffCall { api.deleteSeries(id) }

    suspend fun assignBooks(id: Int, bookIds: List<Int>): ApiResult<AssignResult> =
        session.staffCall { api.assignBooks(id, BookIdsRequest(bookIds.distinct().take(200))) }

    /** A blank [number] clears it (sent as JSON null, not dropped). */
    suspend fun setSeriesNumber(id: Int, bookId: Int, number: String?): ApiResult<StaffSeriesDetail> {
        val value = number.blankToNull()?.take(50)
        val body = buildJsonObject { put("series_number", value?.let { JsonPrimitive(it) } ?: JsonNull) }
        return session.staffCall { api.setSeriesNumber(id, bookId, body) }
    }

    suspend fun removeFromSeries(id: Int, bookId: Int): ApiResult<StaffSeriesDetail> = session.staffCall { api.removeFromSeries(id, bookId) }

    suspend fun parentWork(id: Int, title: String): ApiResult<ParentWorkResult> =
        session.staffCall { api.parentWork(id, ParentWorkRequest(title.trim().take(255))) }

    /** `meta.existing` of a 409 `duplicate` on a series create/rename. */
    fun existingSeries(failure: ApiResult.Failure): IdName? =
        failure.meta?.existing?.let { runCatching { network.json.decodeFromJsonElement<IdName>(it) }.getOrNull() }?.takeIf { it.id > 0 }

    // ---------- merges ----------

    suspend fun authorDuplicates(): ApiResult<List<List<DuplicateAuthor>>> = session.staffCall { api.authorDuplicates() }

    suspend fun mergeAuthors(ids: List<Int>, primaryId: Int?, newName: String?): ApiResult<MergeResult> =
        session.staffCall { api.mergeAuthors(MergeRequest(ids.distinct(), primaryId, newName.blankToNull())) }

    suspend fun mergePublishers(ids: List<Int>, primaryId: Int?, newName: String?): ApiResult<MergeResult> =
        session.staffCall { api.mergePublishers(MergeRequest(ids.distinct(), primaryId, newName.blankToNull())) }

    suspend fun mergeGenre(sourceId: Int, targetId: Int): ApiResult<GenreMergeResult> =
        session.staffCall { api.mergeGenre(sourceId, TargetRequest(targetId)) }

    // ---------- labels ----------

    suspend fun labelSettings(): ApiResult<LabelSettings> = session.staffCall { api.labelSettings() }

    suspend fun saveLabelSettings(body: LabelSettings): ApiResult<LabelSettings> = session.staffCall { api.saveLabelSettings(body) }

    // ---------- imports ----------

    /**
     * Upload [bytes] as a new import job. [updateFields] is a csv-only comma list (null = all
     * families, the server default).
     */
    suspend fun createImport(
        kind: ImportKind,
        fileName: String,
        bytes: ByteArray,
        mime: String?,
        enableScraping: Boolean,
        updateFields: List<String>?,
    ): ApiResult<ImportJob> {
        val type = (mime ?: "text/csv").toMediaTypeOrNull() ?: "text/csv".toMediaType()
        val part = MultipartBody.Part.createFormData("file", fileName, bytes.toRequestBody(type))
        val text = "text/plain".toMediaType()
        val fields = updateFields?.takeIf { kind == ImportKind.CSV }?.joinToString(",")?.toRequestBody(text)
        return session.staffCall {
            api.createImport(part, kind.wire.toRequestBody(text), (if (enableScraping) "1" else "0").toRequestBody(text), fields)
        }
    }

    /** One chunk from [start]; with scraping the server may need minutes, so the long client is used. */
    suspend fun importChunk(id: String, start: Int, scraping: Boolean): ApiResult<ImportJob> {
        val target = if (scraping) network.longStaffContentApi() else api
        return session.staffCall { target.importChunk(id, ChunkRequest(start = start)) }
    }

    suspend fun importJob(id: String): ApiResult<ImportJob> = session.staffCall { api.importJob(id) }

    suspend fun cancelImport(id: String): ApiResult<Unit> = session.staffCall { api.cancelImport(id) }.map { }

    suspend fun imports(): ApiResult<ImportsOverview> = session.staffCall { api.imports(limit = 30) }

    // ---------- covers / enrichment ----------

    suspend fun missingCovers(cursor: String? = null, limit: Int = 50): ApiResult<StaffPage<MissingCover>> =
        session.staffCall { api.missingCovers(cursor, limit) }.toPage()

    suspend fun fetchCover(bookId: Int): ApiResult<FetchCoverResult> = session.staffCall { api.fetchCover(bookId) }

    suspend fun syncCovers(): ApiResult<SyncResult> = session.staffCall { network.longStaffContentApi().syncCovers() }

    suspend fun enrich(): ApiResult<EnrichOverview> = session.staffCall { api.enrich() }

    suspend fun enrichBatch(): ApiResult<EnrichBatchResult> = session.staffCall { network.longStaffContentApi().enrichBatch() }

    /**
     * Upload a digital file (`ebook` or `audio`) streamed from [open] (an audiobook can be large:
     * it is never read into memory). Uses the long-timeout client.
     */
    suspend fun uploadDigital(
        bookId: Int,
        kind: String,
        fileName: String,
        mime: String?,
        length: Long,
        open: () -> java.io.InputStream,
    ): ApiResult<com.pinakes.app.data.model.DigitalUploadResult> {
        val type = mime?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val body = StreamingBody(type, length, open)
        val part = MultipartBody.Part.createFormData("file", fileName, body)
        return session.staffCall { network.longStaffContentApi().uploadDigital(bookId, part, kind.toRequestBody("text/plain".toMediaType())) }
    }

    suspend fun setEnrichCron(enabled: Boolean): ApiResult<CronResult> = session.staffCall { api.enrichCron(CronRequest(enabled)) }
}
