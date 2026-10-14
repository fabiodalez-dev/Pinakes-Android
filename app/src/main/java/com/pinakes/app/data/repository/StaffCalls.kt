package com.pinakes.app.data.repository

import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.apiCall
import com.pinakes.app.data.store.SessionStore

/**
 * One staff call: [apiCall] plus the Phase 1 role contract. A 403 `forbidden` from the API's
 * own envelope means the server no longer considers this user staff, so the stored role is
 * dropped (every staff entry point disappears) and the failure is returned for the screen to
 * leave. Every staff repository method goes through this.
 */
internal suspend fun <T> SessionStore.staffCall(block: suspend () -> Envelope<T>): ApiResult<T> {
    val res = apiCall(block)
    if (res is ApiResult.Failure && res.isForbidden()) saveRole(null)
    return res
}

/** One page of a staff list. */
data class StaffPage<T>(val items: List<T>, val nextCursor: String?, val totalCount: Int? = null)

/** A list answer as a page: the cursor only counts while the server says there is more. */
internal fun <T> ApiResult<List<T>>.toPage(): ApiResult<StaffPage<T>> = when (this) {
    is ApiResult.Success -> {
        val next = meta?.nextCursor?.takeIf { it.isNotBlank() && meta.hasMore != false }
        ApiResult.Success(StaffPage(data, next, meta?.totalCount), meta)
    }
    is ApiResult.Failure -> this
}

/** Map a success, keep a failure. */
internal inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data), meta)
    is ApiResult.Failure -> this
}

/** Blank text → null, so optional keys are left out of the request body. */
internal fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
