package com.pinakes.app.data.repository

import com.pinakes.app.data.model.AddCopiesRequest
import com.pinakes.app.data.model.AddCopiesResult
import com.pinakes.app.data.model.CoverResult
import com.pinakes.app.data.model.CoverUrlRequest
import com.pinakes.app.data.model.DeleteResult
import com.pinakes.app.data.model.ExistingBook
import com.pinakes.app.data.model.IsbnLookup
import com.pinakes.app.data.model.NextPosition
import com.pinakes.app.data.model.Shelf
import com.pinakes.app.data.model.ShelfLevel
import com.pinakes.app.data.model.ShelfLevelWrite
import com.pinakes.app.data.model.ShelfWrite
import com.pinakes.app.data.model.StaffAuthor
import com.pinakes.app.data.model.StaffAuthorRow
import com.pinakes.app.data.model.StaffAuthorWrite
import com.pinakes.app.data.model.StaffBook
import com.pinakes.app.data.model.StaffBookRow
import com.pinakes.app.data.model.StaffGenreCreated
import com.pinakes.app.data.model.StaffGenreNode
import com.pinakes.app.data.model.StaffGenreWrite
import com.pinakes.app.data.model.StaffLookups
import com.pinakes.app.data.model.StaffPublisher
import com.pinakes.app.data.model.StaffPublisherWrite
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.apiCall
import com.pinakes.app.data.store.SessionStore
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** One page of the staff book list. */
data class StaffBooksPage(val items: List<StaffBookRow>, val nextCursor: String?)

/**
 * Staff content management (`/staff/...`). One method per endpoint, each wrapped in [apiCall].
 *
 * A 403 `forbidden` means the server no longer considers this user staff (role changed while
 * the app was open): the stored role is dropped so the staff entry points disappear, and the
 * failure is returned for the screen to leave.
 */
class StaffRepository(
    private val network: NetworkModule,
    private val session: SessionStore,
) {
    private val api get() = network.staffApi()

    private suspend fun <T> staffCall(block: suspend () -> com.pinakes.app.data.model.Envelope<T>): ApiResult<T> {
        val res = apiCall(block)
        if (res is ApiResult.Failure && res.isForbidden()) session.saveRole(null)
        return res
    }

    suspend fun lookups(): ApiResult<StaffLookups> = staffCall { api.lookups() }

    suspend fun isbnLookup(code: String): ApiResult<IsbnLookup> = staffCall { api.isbnLookup(code) }

    suspend fun books(
        query: String?,
        cursor: String?,
        limit: Int = 30,
        authorId: Int? = null,
        publisher: String? = null,
    ): ApiResult<StaffBooksPage> =
        when (val res = staffCall {
            api.books(q = query?.takeIf { it.isNotBlank() }, cursor = cursor, limit = limit, authorId = authorId, publisher = publisher)
        }) {
            is ApiResult.Success -> {
                val meta = res.meta
                val next = meta?.nextCursor?.takeIf { it.isNotBlank() && meta.hasMore != false }
                ApiResult.Success(StaffBooksPage(res.data, next), meta)
            }
            is ApiResult.Failure -> res
        }

    suspend fun book(id: Int): ApiResult<StaffBook> = staffCall { api.book(id) }

    suspend fun createBook(body: JsonObject): ApiResult<StaffBook> = staffCall { api.createBook(body) }

    suspend fun updateBook(id: Int, body: JsonObject): ApiResult<StaffBook> = staffCall { api.updateBook(id, body) }

    suspend fun deleteBook(id: Int): ApiResult<DeleteResult> = staffCall { api.deleteBook(id) }

    suspend fun addCopies(id: Int, quantity: Int): ApiResult<AddCopiesResult> =
        staffCall { api.addCopies(id, AddCopiesRequest(quantity = quantity.coerceIn(1, 100))) }

    /** Multipart cover upload (`cover` field, JPEG or PNG, at most 2 MB server-side). */
    suspend fun uploadCover(id: Int, bytes: ByteArray, mime: String): ApiResult<CoverResult> {
        val type = if (mime == "image/png") "image/png" else "image/jpeg"
        val ext = if (type == "image/png") "png" else "jpg"
        val part = MultipartBody.Part.createFormData("cover", "cover.$ext", bytes.toRequestBody(type.toMediaType()))
        return staffCall { api.uploadCover(id, part) }
    }

    suspend fun coverFromUrl(id: Int, url: String): ApiResult<CoverResult> =
        staffCall { api.coverFromUrl(id, CoverUrlRequest(url.trim())) }

    suspend fun authors(query: String?, limit: Int = 20): ApiResult<List<StaffAuthorRow>> =
        staffCall { api.authors(q = query?.takeIf { it.isNotBlank() }, limit = limit) }

    suspend fun author(id: Int): ApiResult<StaffAuthor> = staffCall { api.author(id) }

    suspend fun saveAuthor(id: Int?, body: StaffAuthorWrite): ApiResult<StaffAuthor> =
        staffCall { if (id == null || id <= 0) api.createAuthor(body) else api.updateAuthor(id, body) }

    suspend fun publishers(query: String?, limit: Int = 20): ApiResult<List<StaffPublisher>> =
        staffCall { api.publishers(q = query?.takeIf { it.isNotBlank() }, limit = limit) }

    suspend fun publisher(id: Int): ApiResult<StaffPublisher> = staffCall { api.publisher(id) }

    suspend fun savePublisher(id: Int?, body: StaffPublisherWrite): ApiResult<StaffPublisher> =
        staffCall { if (id == null || id <= 0) api.createPublisher(body) else api.updatePublisher(id, body) }

    suspend fun genres(): ApiResult<List<StaffGenreNode>> = staffCall { api.genres() }

    suspend fun createGenre(name: String, parentId: Int?): ApiResult<StaffGenreCreated> =
        staffCall { api.createGenre(StaffGenreWrite(name.trim(), parentId)) }

    suspend fun shelves(): ApiResult<List<Shelf>> = staffCall { api.shelves() }

    suspend fun createShelf(code: String, name: String?, order: Int?): ApiResult<Shelf> =
        staffCall { api.createShelf(ShelfWrite(code.trim(), name?.trim()?.takeIf { it.isNotEmpty() }, order)) }

    suspend fun createLevel(shelfId: Int, number: Int, description: String?): ApiResult<ShelfLevel> =
        staffCall { api.createLevel(shelfId, ShelfLevelWrite(number, description?.trim()?.takeIf { it.isNotEmpty() })) }

    suspend fun nextPosition(shelfId: Int, levelId: Int, bookId: Int?): ApiResult<NextPosition> =
        staffCall { api.nextPosition(shelfId, levelId, bookId?.takeIf { it > 0 }) }

    /** Decode `meta.existing` of a 409 `duplicate` as a book, if it is one. */
    fun existingBook(failure: ApiResult.Failure): ExistingBook? = decodeExisting(failure.meta?.existing)

    /** Decode `meta.existing` of a 409 `duplicate` on authors/publishers (`{id,name}`). */
    fun existingEntity(failure: ApiResult.Failure): com.pinakes.app.data.model.IdName? =
        failure.meta?.existing?.let { runCatching { network.json.decodeFromJsonElement<com.pinakes.app.data.model.IdName>(it) }.getOrNull() }
            ?.takeIf { it.id > 0 }

    private fun decodeExisting(raw: JsonElement?): ExistingBook? =
        raw?.let { runCatching { network.json.decodeFromJsonElement<ExistingBook>(it) }.getOrNull() }?.takeIf { it.id > 0 }
}

/**
 * The server's role guard refused the call: 403 with `error.code == "forbidden"` in the API's
 * own envelope. A bare 403 (reverse proxy, WAF, CDN) or `app_access_disabled` is not a role
 * change, so it must not drop the stored role nor close the screen.
 */
fun ApiResult.Failure.isForbidden(): Boolean = fromEnvelope && code == ErrorCodes.FORBIDDEN

/** 409 `duplicate` (identifier or name already used). */
fun ApiResult.Failure.isDuplicate(): Boolean =
    code == ErrorCodes.DUPLICATE || (httpStatus == 409 && meta?.existing != null)
