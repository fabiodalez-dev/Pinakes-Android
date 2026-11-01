package com.pinakes.app.data.network

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
import com.pinakes.app.data.model.MetaEnvelope
import com.pinakes.app.data.model.PatronPhotoState
import com.pinakes.app.data.model.PatronType
import com.pinakes.app.data.model.PatronTypeRequest
import com.pinakes.app.data.model.PatronTypeUpdateMeta
import com.pinakes.app.data.model.PatronTypesMeta
import com.pinakes.app.data.model.StaffLedger
import com.pinakes.app.data.model.StaffUser
import com.pinakes.app.data.model.UserImportCancelResult
import com.pinakes.app.data.model.UserImportChunkRequest
import com.pinakes.app.data.model.UserImportJob
import com.pinakes.app.data.model.UserImportStartRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
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
import retrofit2.http.Streaming

/**
 * Pinakes 0.9.0 patron tools (Mobile API 1.10.0): patron types, card renewal and photo
 * (`staff-patrons`), the ledger (`staff-ledger`, module fines), the visitor log
 * (`staff-attendance`, module visitor_log) and the patron CSV import (`staff-user-import`).
 * PDFs and CSVs go through [StaffContentApi.download] like every staff export.
 */
interface Staff090PatronsApi {

    // ---- Patron record ----

    @GET("staff/patron-types")
    suspend fun patronTypes(): MetaEnvelope<List<PatronType>, PatronTypesMeta>

    @PUT("staff/users/{id}/patron-type")
    suspend fun setPatronType(@Path("id") id: Int, @Body body: PatronTypeRequest): MetaEnvelope<StaffUser, PatronTypeUpdateMeta>

    /** [key]: a retried renewal must not add the card duration twice. */
    @POST("staff/users/{id}/renew-card")
    suspend fun renewCard(@Path("id") id: Int, @Header("Idempotency-Key") key: String?): Envelope<StaffUser>

    /** The photo needs the bearer token, so it never goes through the image loader. */
    @Streaming
    @GET("staff/users/{id}/photo")
    suspend fun photo(@Path("id") id: Int): Response<ResponseBody>

    @Multipart
    @POST("staff/users/{id}/photo")
    suspend fun uploadPhoto(@Path("id") id: Int, @Part photo: MultipartBody.Part): Envelope<PatronPhotoState>

    @DELETE("staff/users/{id}/photo")
    suspend fun deletePhoto(@Path("id") id: Int): Envelope<PatronPhotoState>

    // ---- Ledger (Idempotency-Key required on every write) ----

    @GET("staff/users/{id}/ledger")
    suspend fun ledger(@Path("id") id: Int, @Query("cursor") cursor: String? = null, @Query("limit") limit: Int? = null): Envelope<StaffLedger>

    @POST("staff/users/{id}/ledger/payments")
    suspend fun payment(@Path("id") id: Int, @Body body: LedgerPaymentRequest, @Header("Idempotency-Key") key: String): Envelope<LedgerWriteResult>

    @POST("staff/users/{id}/ledger/charges")
    suspend fun charge(@Path("id") id: Int, @Body body: LedgerChargeRequest, @Header("Idempotency-Key") key: String): Envelope<LedgerWriteResult>

    @POST("staff/users/{id}/ledger/waivers")
    suspend fun waiver(@Path("id") id: Int, @Body body: LedgerWaiverRequest, @Header("Idempotency-Key") key: String): Envelope<LedgerWriteResult>

    // ---- Visitor log ----

    @GET("staff/attendance")
    suspend fun attendance(@Query("days") days: Int): Envelope<AttendanceOverview>

    @POST("staff/attendance/check-ins")
    suspend fun checkIn(@Body body: AttendanceCheckInRequest, @Header("Idempotency-Key") key: String?): Envelope<AttendanceVisit>

    @POST("staff/attendance/guests")
    suspend fun guest(@Body body: AttendanceGuestRequest, @Header("Idempotency-Key") key: String?): Envelope<AttendanceVisit>

    @PUT("staff/attendance/settings")
    suspend fun attendanceSettings(@Body body: AttendanceSettingsRequest): Envelope<AttendanceSettingsResult>

    @POST("staff/attendance/purge")
    suspend fun attendancePurge(): Envelope<AttendancePurgeResult>

    // ---- Patron CSV import ----

    @Multipart
    @POST("staff/user-imports")
    suspend fun createUserImport(@Part file: MultipartBody.Part, @Part("delimiter") delimiter: RequestBody): Envelope<UserImportJob>

    @GET("staff/user-imports/{id}")
    suspend fun userImport(@Path("id") id: String): Envelope<UserImportJob>

    @POST("staff/user-imports/{id}/start")
    suspend fun startUserImport(@Path("id") id: String, @Body body: UserImportStartRequest): Envelope<UserImportJob>

    @POST("staff/user-imports/{id}/chunk")
    suspend fun userImportChunk(@Path("id") id: String, @Body body: UserImportChunkRequest): Envelope<UserImportJob>

    @DELETE("staff/user-imports/{id}")
    suspend fun cancelUserImport(@Path("id") id: String): Envelope<UserImportCancelResult>
}
