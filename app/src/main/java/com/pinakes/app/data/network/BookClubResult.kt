package com.pinakes.app.data.network

import com.pinakes.app.data.model.BookClubEnvelope
import retrofit2.HttpException
import java.io.IOException

/**
 * Bridges the Book Club envelope (`{success, data, error}`) into the shared [ApiResult] the
 * whole app already switches on — the analogue of [apiCall] for [BookClubApi].
 *
 * Success mapping (data-bearing endpoints):
 *  - `error != null`          → [ApiResult.Failure] carrying the plugin's error code + message.
 *  - `data != null`           → [ApiResult.Success].
 *  - success but no data      → [ApiResult.Failure] (a data-bearing endpoint MUST return data;
 *                               a bodiless success is a server/proxy fault, not a Unit result).
 *
 * `Envelope<Unit>` endpoints (health, propose, vote, rsvp, progress) carry no payload and MUST
 * use [bookClubCallUnit] instead — otherwise a legitimate no-data success here would map to a
 * spurious failure. Keeping the two paths separate removes the old `Unit as T` unchecked cast,
 * which crashed a data-bearing caller with ClassCastException on a bodiless success.
 *
 * HTTP failures reuse the core pipeline ([HttpException.toFailure] → [parseErrorBody]): the
 * plugin's error body decodes through the same `Envelope<Unit>` shape (its extra `success`
 * key is ignored, `error.{code,message}` is shared), and bodiless errors get the same
 * localized status-derived fallback messages as the core API.
 */
suspend fun <T> bookClubCall(block: suspend () -> BookClubEnvelope<T>): ApiResult<T> = try {
    val envelope = block()
    when {
        envelope.error != null ->
            ApiResult.Failure(envelope.error!!.code, envelope.error!!.message, httpStatus = 200)
        envelope.data != null -> ApiResult.Success(envelope.data!!)
        else -> ApiResult.Failure(ErrorCodes.UNKNOWN, "Empty response body", 200)
    }
} catch (e: HttpException) {
    e.toFailure()
} catch (e: IOException) {
    ApiResult.Failure(ErrorCodes.NETWORK, e.message ?: "Network error", 0)
} catch (e: Throwable) {
    ApiResult.Failure(ErrorCodes.UNKNOWN, e.message ?: "Unexpected error", 0)
}

/**
 * Envelope<Unit> variant: the endpoint carries no payload, so any non-error success maps to
 * [ApiResult.Success] of `Unit`. Same HTTP/exception mapping as [bookClubCall].
 */
suspend fun bookClubCallUnit(block: suspend () -> BookClubEnvelope<Unit>): ApiResult<Unit> = try {
    val envelope = block()
    if (envelope.error != null) {
        ApiResult.Failure(envelope.error!!.code, envelope.error!!.message, httpStatus = 200)
    } else {
        ApiResult.Success(Unit)
    }
} catch (e: HttpException) {
    e.toFailure()
} catch (e: IOException) {
    ApiResult.Failure(ErrorCodes.NETWORK, e.message ?: "Network error", 0)
} catch (e: Throwable) {
    ApiResult.Failure(ErrorCodes.UNKNOWN, e.message ?: "Unexpected error", 0)
}
