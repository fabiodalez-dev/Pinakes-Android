package com.pinakes.app.data.network

import com.pinakes.app.data.model.BookClubEnvelope
import retrofit2.HttpException
import java.io.IOException

/**
 * Bridges the Book Club envelope (`{success, data, error}`) into the shared [ApiResult] the
 * whole app already switches on — the analogue of [apiCall] for [BookClubApi].
 *
 * Success mapping:
 *  - `error != null`          → [ApiResult.Failure] carrying the plugin's error code + message.
 *  - `data != null`           → [ApiResult.Success].
 *  - no data (no-content ok)  → [ApiResult.Success] of `Unit` (the `Envelope<Unit>` endpoints).
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
        else -> {
            @Suppress("UNCHECKED_CAST")
            ApiResult.Success(Unit as T)
        }
    }
} catch (e: HttpException) {
    e.toFailure()
} catch (e: IOException) {
    ApiResult.Failure(ErrorCodes.NETWORK, e.message ?: "Network error", 0)
} catch (e: Throwable) {
    ApiResult.Failure(ErrorCodes.UNKNOWN, e.message ?: "Unexpected error", 0)
}
