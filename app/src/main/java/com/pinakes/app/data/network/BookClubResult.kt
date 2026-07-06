package com.pinakes.app.data.network

import com.pinakes.app.data.model.BookClubEnvelope
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Bridges the Book Club envelope (`{success, data, error}`) into the shared [ApiResult] the
 * whole app already switches on — the analogue of [apiCall] for [BookClubApi].
 *
 * Success mapping:
 *  - `error != null`          → [ApiResult.Failure] carrying the plugin's error code + message.
 *  - `data != null`           → [ApiResult.Success].
 *  - no data (no-content ok)  → [ApiResult.Success] of `Unit` (e.g. the `propose` body we ignore).
 *
 * Failure mapping mirrors [apiCall]: HTTP errors (4xx/5xx) arrive as [HttpException]; the plugin
 * error body is parsed for its `code`/`message`, falling back to a status-derived code so the
 * caller can still branch (and so [ApiResult.Failure.httpStatus] tells `404` — "section off" —
 * apart from a network error).
 */
private val bookClubErrorJson = Json { ignoreUnknownKeys = true; isLenient = true }

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
    e.toBookClubFailure()
} catch (e: IOException) {
    ApiResult.Failure(ErrorCodes.NETWORK, e.message ?: "Network error", 0)
} catch (e: Throwable) {
    ApiResult.Failure(ErrorCodes.UNKNOWN, e.message ?: "Unexpected error", 0)
}

private fun HttpException.toBookClubFailure(): ApiResult.Failure {
    val status = code()
    val retryAfter = response()?.headers()?.get("Retry-After")?.toLongOrNull()
    val body = try { response()?.errorBody()?.string() } catch (_: Throwable) { null }
    val parsed = parseBookClubErrorBody(body, status)
    return parsed.copy(retryAfterSeconds = parsed.retryAfterSeconds ?: retryAfter)
}

/** Parse a Book Club error envelope; fall back to a status-derived code/message when absent. */
internal fun parseBookClubErrorBody(body: String?, status: Int): ApiResult.Failure {
    val error = body?.takeIf { it.isNotBlank() }?.let {
        runCatching { bookClubErrorJson.decodeFromString<BookClubEnvelope<Unit>>(it).error }.getOrNull()
    }
    val code = error?.code?.takeIf { it.isNotBlank() } ?: bookClubCodeForStatus(status)
    val message = error?.message?.takeIf { it.isNotBlank() } ?: ""
    return ApiResult.Failure(code = code, message = message, httpStatus = status)
}

private fun bookClubCodeForStatus(status: Int): String = when (status) {
    400, 422 -> ErrorCodes.VALIDATION
    401 -> ErrorCodes.UNAUTHORIZED
    403 -> ErrorCodes.FORBIDDEN
    404 -> ErrorCodes.NOT_FOUND
    409 -> ErrorCodes.CONFLICT
    429 -> ErrorCodes.RATE_LIMITED
    in 500..599 -> ErrorCodes.SERVER_ERROR
    else -> ErrorCodes.UNKNOWN
}
