package com.pinakes.app.data.repository

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
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.Staff090StocktakeApi
import com.pinakes.app.data.store.SessionStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inventory stocktake (Mobile API 1.10.0). Every call goes through [staffCall]: a 403
 * `forbidden` (staff role gate) drops the role, a 403 `not_permitted` (an administrator-only
 * close) leaves it alone and is shown with the server's message.
 */
@Singleton
class Staff090StocktakeRepository @Inject constructor(
    private val network: NetworkModule,
    private val session: SessionStore,
) {
    private val api get() = network.typedApi(Staff090StocktakeApi::class.java)

    suspend fun options(): ApiResult<Staff090StocktakeOptions> = session.staffCall { api.options() }

    /** [status] null = every session (`all`). */
    suspend fun sessions(status: String?, cursor: String? = null): ApiResult<StaffPage<Staff090StocktakeSession>> =
        session.staffCall { api.sessions(status ?: "all", cursor, PAGE) }.toPage()

    suspend fun create(body: Staff090StocktakeCreateRequest, key: String): ApiResult<Staff090StocktakeSession> =
        session.staffCall { api.create(body, key) }

    suspend fun session(id: Int, mine: Boolean): ApiResult<Staff090StocktakeSession> =
        session.staffCall { api.session(id, if (mine) 1 else null) }

    suspend fun state(id: Int, mine: Boolean): ApiResult<Staff090StocktakeState> =
        session.staffCall { api.state(id, if (mine) 1 else null) }

    suspend fun scans(id: Int, outcome: String?, mine: Boolean, cursor: String? = null): ApiResult<StaffPage<Staff090StocktakeScan>> =
        session.staffCall { api.scans(id, outcome, if (mine) 1 else null, cursor, PAGE) }.toPage()

    suspend fun scan(id: Int, body: Staff090StocktakeScanRequest, key: String): ApiResult<Staff090StocktakeScanResult> =
        session.staffCall { api.scan(id, body, key) }

    suspend fun report(id: Int, category: String?, cursor: String? = null): ApiResult<StaffPage<Staff090StocktakeReportRow>> =
        session.staffCall { api.report(id, category, cursor, PAGE) }.toPage()

    /** A list of codes; the server runs every code at once, so the long-timeout client is used. */
    suspend fun upload(id: Int, fileName: String, bytes: ByteArray, mime: String?, shelfId: Int?): ApiResult<Staff090StocktakeUpload> {
        val type = (mime ?: "text/plain").toMediaTypeOrNull() ?: "text/plain".toMediaType()
        val part = MultipartBody.Part.createFormData("file", fileName, bytes.toRequestBody(type))
        val shelf = shelfId?.toString()?.toRequestBody("text/plain".toMediaType())
        return session.staffCall { network.longTypedApi(Staff090StocktakeApi::class.java).upload(id, part, shelf) }
    }

    suspend fun close(id: Int, missingAction: String): ApiResult<Staff090StocktakeSession> =
        session.staffCall { network.longTypedApi(Staff090StocktakeApi::class.java).close(id, Staff090StocktakeCloseRequest(missingAction)) }

    suspend fun cancel(id: Int): ApiResult<Staff090StocktakeSession> = session.staffCall { api.cancel(id) }

    companion object {
        const val PAGE = 50

        /** The report downloads, relative to `/api/v1/` for [StaffFilesRepository.download]. */
        fun reportCsvPath(id: Int) = "staff/stocktake/sessions/$id/report.csv"
        fun reportPdfPath(id: Int) = "staff/stocktake/sessions/$id/report.pdf"
    }
}
