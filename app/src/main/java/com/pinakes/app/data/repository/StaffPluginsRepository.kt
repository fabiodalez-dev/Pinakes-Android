package com.pinakes.app.data.repository

import com.pinakes.app.data.model.ArchiveLookups
import com.pinakes.app.data.model.AttachAuthorityRequest
import com.pinakes.app.data.model.AuthorityDetail
import com.pinakes.app.data.model.AuthorityRow
import com.pinakes.app.data.model.DeleteResult
import com.pinakes.app.data.model.ReceiveOfferRequest
import com.pinakes.app.data.model.StaffArchiveDetail
import com.pinakes.app.data.model.StaffArchiveRow
import com.pinakes.app.data.model.StaffOffer
import com.pinakes.app.data.model.StaffWantedBook
import com.pinakes.app.data.model.WantedReceiveResult
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.store.SessionStore
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Staff management of plugin content (Mobile API 1.7.0): Desiderata offers and Archives. */
class StaffPluginsRepository(
    private val network: NetworkModule,
    private val session: SessionStore,
) {
    private val api get() = network.staffPluginsApi()

    // ---------- desiderata ----------

    suspend fun offers(status: String, query: String? = null, cursor: String? = null): ApiResult<StaffPage<StaffOffer>> =
        session.staffCall { api.offers(status, query.blankToNull(), cursor, 30) }.toPage()

    suspend fun acceptOffer(id: Int): ApiResult<StaffOffer> = session.staffCall { api.acceptOffer(id) }

    suspend fun rejectOffer(id: Int): ApiResult<StaffOffer> = session.staffCall { api.rejectOffer(id) }

    suspend fun receiveOffer(id: Int, bookId: Int?): ApiResult<StaffOffer> =
        session.staffCall { api.receiveOffer(id, ReceiveOfferRequest(bookId?.takeIf { it > 0 })) }

    suspend fun deleteOffer(id: Int): ApiResult<DeleteResult> = session.staffCall { api.deleteOffer(id) }

    suspend fun wanted(query: String? = null, cursor: String? = null): ApiResult<StaffPage<StaffWantedBook>> =
        session.staffCall { api.wanted(query.blankToNull(), cursor, 30) }.toPage()

    suspend fun receiveWanted(bookId: Int): ApiResult<WantedReceiveResult> = session.staffCall { api.receiveWanted(bookId) }

    // ---------- archives ----------

    suspend fun archiveLookups(): ApiResult<ArchiveLookups> = session.staffCall { api.archiveLookups() }

    /** [published] is `all`, `1` or `0`. */
    suspend fun archives(query: String?, published: String, cursor: String?): ApiResult<StaffPage<StaffArchiveRow>> =
        session.staffCall { api.archives(q = query.blankToNull(), published = published, cursor = cursor, limit = 30) }.toPage()

    suspend fun archive(id: Int): ApiResult<StaffArchiveDetail> = session.staffCall { api.archive(id) }

    suspend fun saveArchive(id: Int?, body: JsonObject): ApiResult<StaffArchiveDetail> =
        session.staffCall { if (id == null || id <= 0) api.createArchive(body) else api.updateArchive(id, body) }

    suspend fun deleteArchive(id: Int): ApiResult<DeleteResult> = session.staffCall { api.deleteArchive(id) }

    suspend fun uploadArchiveCover(id: Int, fileName: String, bytes: ByteArray, mime: String): ApiResult<StaffArchiveDetail> {
        val part = MultipartBody.Part.createFormData("cover", fileName, bytes.toRequestBody(mime.toMediaTypeOrNull() ?: "image/jpeg".toMediaType()))
        return session.staffCall { api.uploadArchiveCover(id, part) }
    }

    suspend fun deleteArchiveCover(id: Int): ApiResult<StaffArchiveDetail> = session.staffCall { api.deleteArchiveCover(id) }

    suspend fun uploadArchiveDocument(id: Int, fileName: String, bytes: ByteArray, mime: String?): ApiResult<StaffArchiveDetail> {
        val type = mime?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val part = MultipartBody.Part.createFormData("document", fileName, bytes.toRequestBody(type))
        return session.staffCall { api.uploadArchiveDocument(id, part) }
    }

    suspend fun deleteArchiveDocument(id: Int, fileId: Int): ApiResult<StaffArchiveDetail> =
        session.staffCall { api.deleteArchiveDocument(id, fileId) }

    suspend fun attachAuthority(id: Int, authorityId: Int, role: String): ApiResult<StaffArchiveDetail> =
        session.staffCall { api.attachAuthority(id, AttachAuthorityRequest(authorityId, role)) }

    suspend fun detachAuthority(id: Int, authorityId: Int): ApiResult<StaffArchiveDetail> =
        session.staffCall { api.detachAuthority(id, authorityId) }

    suspend fun authorities(query: String?, type: String? = null, cursor: String? = null): ApiResult<StaffPage<AuthorityRow>> =
        session.staffCall { api.authorities(query.blankToNull(), type.blankToNull(), cursor, 30) }.toPage()

    suspend fun authority(id: Int): ApiResult<AuthorityDetail> = session.staffCall { api.authority(id) }

    suspend fun saveAuthority(id: Int?, body: JsonObject): ApiResult<AuthorityDetail> =
        session.staffCall { if (id == null || id <= 0) api.createAuthority(body) else api.updateAuthority(id, body) }

    suspend fun deleteAuthority(id: Int): ApiResult<DeleteResult> = session.staffCall { api.deleteAuthority(id) }
}
