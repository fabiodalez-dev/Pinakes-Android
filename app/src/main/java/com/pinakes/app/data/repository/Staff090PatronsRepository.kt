package com.pinakes.app.data.repository

import com.pinakes.app.data.model.AttendanceCheckInRequest
import com.pinakes.app.data.model.AttendanceGuestRequest
import com.pinakes.app.data.model.AttendanceOverview
import com.pinakes.app.data.model.AttendancePurgeResult
import com.pinakes.app.data.model.AttendanceSettingsRequest
import com.pinakes.app.data.model.AttendanceSettingsResult
import com.pinakes.app.data.model.AttendanceVisit
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.LedgerChargeRequest
import com.pinakes.app.data.model.LedgerPaymentRequest
import com.pinakes.app.data.model.LedgerWaiverRequest
import com.pinakes.app.data.model.LedgerWriteResult
import com.pinakes.app.data.model.PatronPhotoState
import com.pinakes.app.data.model.PatronTypeList
import com.pinakes.app.data.model.PatronTypeRequest
import com.pinakes.app.data.model.PatronTypeUpdate
import com.pinakes.app.data.model.StaffLedger
import com.pinakes.app.data.model.StaffUser
import com.pinakes.app.data.model.UserImportCancelResult
import com.pinakes.app.data.model.UserImportChunkRequest
import com.pinakes.app.data.model.UserImportJob
import com.pinakes.app.data.model.UserImportStartRequest
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.Staff090PatronsApi
import com.pinakes.app.data.network.parseErrorBody
import com.pinakes.app.data.store.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/** A ledger write: the three desk forms of the web reader record. */
sealed interface LedgerWrite {
    val amountCents: Int
    data class Payment(override val amountCents: Int, val method: String, val note: String?) : LedgerWrite
    data class Charge(override val amountCents: Int, val description: String) : LedgerWrite
    data class Waiver(override val amountCents: Int, val reason: String) : LedgerWrite
}

/**
 * The Pinakes 0.9.0 patron tools (Mobile API 1.10.0): patron record, ledger, visitor log and
 * patron CSV import. Every call goes through [staffCall], so a demoted operator loses the staff
 * area on the first refused call. Writes that may be retried carry the caller's
 * Idempotency-Key (mandatory on the ledger).
 */
@Singleton
class Staff090PatronsRepository @Inject constructor(
    private val network: NetworkModule,
    private val session: SessionStore,
) {
    private val api get() = network.typedApi(Staff090PatronsApi::class.java)

    // ---- Patron record ----

    suspend fun patronTypes(): ApiResult<PatronTypeList> = session.staffCall {
        val e = api.patronTypes()
        Envelope(data = if (e.error == null) PatronTypeList(e.data.orEmpty(), e.meta?.defaultId, e.meta?.canAssign ?: false) else null, error = e.error)
    }

    /** [typeId] null = the default type (sent as 0, which the server reads as "none of its own"). */
    suspend fun setPatronType(userId: Int, typeId: Int?, recomputeCardExpiry: Boolean): ApiResult<PatronTypeUpdate> = session.staffCall {
        val e = api.setPatronType(userId, PatronTypeRequest(typeId ?: 0, recomputeCardExpiry))
        Envelope(data = e.data?.let { PatronTypeUpdate(it, e.meta?.cardExpiryRecomputed ?: false) }, error = e.error)
    }

    suspend fun renewCard(userId: Int, idempotencyKey: String?): ApiResult<StaffUser> = session.staffCall { api.renewCard(userId, idempotencyKey) }

    /** The photo bytes (a JPEG of at most 600 px), kept in memory only: patron data, never cached. */
    suspend fun photo(userId: Int): ApiResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val response = api.photo(userId)
            if (!response.isSuccessful) {
                val failure = parseErrorBody(response.errorBody()?.string(), response.code())
                if (failure.isForbidden()) session.saveRole(null)
                return@withContext failure
            }
            val bytes = response.body()?.use { it.bytes() } ?: return@withContext ApiResult.Failure(ErrorCodes.UNKNOWN, "Empty body", response.code())
            ApiResult.Success(bytes)
        } catch (e: SSLException) {
            ApiResult.Failure(ErrorCodes.TLS, e.message ?: "TLS error")
        } catch (e: IOException) {
            ApiResult.Failure(ErrorCodes.NETWORK, e.message ?: "Network error")
        } catch (e: Throwable) {
            ApiResult.Failure(ErrorCodes.UNKNOWN, e.message ?: "Unexpected error")
        }
    }

    suspend fun uploadPhoto(userId: Int, jpeg: ByteArray): ApiResult<PatronPhotoState> {
        val part = MultipartBody.Part.createFormData("photo", "photo.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
        return session.staffCall { api.uploadPhoto(userId, part) }
    }

    suspend fun deletePhoto(userId: Int): ApiResult<PatronPhotoState> = session.staffCall { api.deletePhoto(userId) }

    // ---- Ledger ----

    suspend fun ledger(userId: Int, cursor: String? = null, limit: Int = 30): ApiResult<StaffPage<StaffLedger>> =
        session.staffCall { api.ledger(userId, cursor, limit) }.let { res ->
            when (res) {
                is ApiResult.Success -> {
                    val next = res.meta?.nextCursor?.takeIf { it.isNotBlank() && res.meta.hasMore != false }
                    ApiResult.Success(StaffPage(listOf(res.data), next, res.meta?.totalCount), res.meta)
                }
                is ApiResult.Failure -> res
            }
        }

    suspend fun recordLedger(userId: Int, write: LedgerWrite, idempotencyKey: String): ApiResult<LedgerWriteResult> = session.staffCall {
        when (write) {
            is LedgerWrite.Payment -> api.payment(userId, LedgerPaymentRequest(write.amountCents, write.method, write.note.blankToNull()?.take(255)), idempotencyKey)
            is LedgerWrite.Charge -> api.charge(userId, LedgerChargeRequest(write.amountCents, write.description.trim().take(255)), idempotencyKey)
            is LedgerWrite.Waiver -> api.waiver(userId, LedgerWaiverRequest(write.amountCents, write.reason.trim().take(255)), idempotencyKey)
        }
    }

    // ---- Visitor log ----

    suspend fun attendance(days: Int): ApiResult<AttendanceOverview> = session.staffCall { api.attendance(days) }

    suspend fun checkIn(cardCode: String, branchId: Int?, idempotencyKey: String?): ApiResult<AttendanceVisit> =
        session.staffCall { api.checkIn(AttendanceCheckInRequest(cardCode = cardCode.trim(), branchId = branchId), idempotencyKey) }

    /** A member picked from the borrower search (Mobile API 1.11.0), in place of a card. */
    suspend fun checkInMember(userId: Int, branchId: Int?, idempotencyKey: String?): ApiResult<AttendanceVisit> =
        session.staffCall { api.checkIn(AttendanceCheckInRequest(userId = userId, branchId = branchId), idempotencyKey) }

    suspend fun guest(name: String?, reason: String?, branchId: Int?, idempotencyKey: String?): ApiResult<AttendanceVisit> =
        session.staffCall { api.guest(AttendanceGuestRequest(name.blankToNull()?.take(150), reason.blankToNull()?.take(255), branchId), idempotencyKey) }

    suspend fun saveRetention(days: Int): ApiResult<AttendanceSettingsResult> = session.staffCall { api.attendanceSettings(AttendanceSettingsRequest(days)) }

    suspend fun purgeAttendance(): ApiResult<AttendancePurgeResult> = session.staffCall { api.attendancePurge() }

    // ---- Patron CSV import ----

    /** [delimiter]: "" (detect), ";", "," or "tab". */
    suspend fun createUserImport(fileName: String, bytes: ByteArray, mime: String?, delimiter: String): ApiResult<UserImportJob> {
        val type = (mime?.takeIf { it.isNotBlank() } ?: "text/csv").toMediaType()
        val part = MultipartBody.Part.createFormData("file", fileName, bytes.toRequestBody(type))
        return session.staffCall { network.longTypedApi(Staff090PatronsApi::class.java).createUserImport(part, delimiter.toRequestBody("text/plain".toMediaType())) }
    }

    /** Jobs started here or on another device, so one left half-way can be finished. */
    suspend fun userImports(cursor: String? = null, limit: Int = 30): ApiResult<StaffPage<UserImportJob>> =
        session.staffCall { api.userImports(cursor, limit) }.toPage()

    suspend fun userImport(id: String): ApiResult<UserImportJob> = session.staffCall { api.userImport(id) }

    suspend fun startUserImport(id: String, mapping: List<String>, sendInvites: Boolean): ApiResult<UserImportJob> =
        session.staffCall { api.startUserImport(id, UserImportStartRequest(mapping, sendInvites)) }

    /** One chunk of 10 rows; invitations are mailed synchronously, so the long client is used. */
    suspend fun userImportChunk(id: String, start: Int): ApiResult<UserImportJob> =
        session.staffCall { network.longTypedApi(Staff090PatronsApi::class.java).userImportChunk(id, UserImportChunkRequest(start)) }

    suspend fun cancelUserImport(id: String): ApiResult<UserImportCancelResult> = session.staffCall { api.cancelUserImport(id) }
}
