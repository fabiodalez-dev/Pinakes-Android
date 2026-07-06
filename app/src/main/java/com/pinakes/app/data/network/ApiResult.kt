package com.pinakes.app.data.network

import com.pinakes.app.data.model.ApiError
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.Meta
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * A normalized result for every API call. Maps the {data, meta, error} envelope and
 * HTTP status families to a small, exhaustive set of cases the UI can switch on.
 */
sealed interface ApiResult<out T> {

    data class Success<T>(val data: T, val meta: Meta? = null) : ApiResult<T>

    /** Mapped failure: carries a stable [code], a human message and the HTTP status. */
    data class Failure(
        val code: String,
        val message: String,
        val httpStatus: Int = 0,
        val retryAfterSeconds: Long? = null,
    ) : ApiResult<Nothing>

    val isSuccess: Boolean get() = this is Success
}

/** Well-known error codes (from `error.code` or derived from the HTTP status). */
object ErrorCodes {
    const val INVALID_CREDENTIALS = "invalid_credentials"
    // Emitted by the server's AppAuthMiddleware (403) when mobile app access is disabled.
    const val APP_ACCESS_DISABLED = "app_access_disabled"
    const val RATE_LIMITED = "rate_limited"
    const val UNAUTHORIZED = "unauthorized"          // 401 without a body code → force re-login
    const val FORBIDDEN = "forbidden"
    const val NOT_FOUND = "not_found"
    const val CONFLICT = "conflict"                  // 409 overlap / unavailable / cannot-cancel
    const val VALIDATION = "validation"              // 400 / 422
    const val SERVER_ERROR = "server_error"          // 5xx
    const val NETWORK = "network"                    // no connectivity / timeout
    const val UNKNOWN = "unknown"
}

private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Discards the typed [Envelope.data] but preserves [Envelope.meta]/[Envelope.error], so an
 * endpoint whose body shape we don't care about can be handled through the no-content
 * `Envelope<Unit>` path of [apiCall].
 */
fun <T> Envelope<T>.asUnit(): Envelope<Unit> = Envelope(data = null, meta = meta, error = error)

/** True when the failure means the token is gone/expired and the app must re-login. */
fun ApiResult.Failure.isAuthExpired(): Boolean =
    httpStatus == 401 || code == ErrorCodes.UNAUTHORIZED || code == ErrorCodes.INVALID_CREDENTIALS && httpStatus == 401

/**
 * Wraps a suspend Retrofit call returning an [Envelope] into an [ApiResult].
 * Use this for endpoints declared as `suspend fun(): Envelope<T>`.
 */
suspend fun <T> apiCall(block: suspend () -> Envelope<T>): ApiResult<T> = try {
    val envelope = block()
    when {
        envelope.error != null -> envelope.error!!.toFailure(httpStatus = 0)
        envelope.data != null -> ApiResult.Success(envelope.data!!, envelope.meta)
        else -> {
            // data is null on success for no-content endpoints (Envelope<Unit> etc.)
            @Suppress("UNCHECKED_CAST")
            ApiResult.Success(Unit as T, envelope.meta)
        }
    }
} catch (e: HttpException) {
    e.toFailure()
} catch (e: IOException) {
    ApiResult.Failure(ErrorCodes.NETWORK, e.message ?: "Network error", 0)
} catch (e: Throwable) {
    ApiResult.Failure(ErrorCodes.UNKNOWN, e.message ?: "Unexpected error", 0)
}

/**
 * Wraps a suspend Retrofit call returning a raw [Response] of an [Envelope] — used where
 * the caller needs status / headers (e.g. ETag, 304). Returns the [Response] for the caller
 * to inspect headers, after validating the envelope.
 */
suspend fun <T> apiResponse(block: suspend () -> Response<Envelope<T>>): Pair<ApiResult<T>, Response<Envelope<T>>?> = try {
    val response = block()
    if (response.isSuccessful) {
        val envelope = response.body()
        val result: ApiResult<T> = when {
            envelope?.error != null -> envelope.error!!.toFailure(response.code())
            envelope?.data != null -> ApiResult.Success(envelope.data!!, envelope.meta)
            else -> ApiResult.Failure(ErrorCodes.UNKNOWN, "Empty body", response.code())
        }
        result to response
    } else {
        parseErrorBody(response.errorBody()?.string(), response.code()) to response
    }
} catch (e: IOException) {
    ApiResult.Failure(ErrorCodes.NETWORK, e.message ?: "Network error", 0) to null
} catch (e: Throwable) {
    ApiResult.Failure(ErrorCodes.UNKNOWN, e.message ?: "Unexpected error", 0) to null
}

private fun ApiError.toFailure(httpStatus: Int): ApiResult.Failure =
    ApiResult.Failure(code = code, message = message, httpStatus = httpStatus)

internal fun HttpException.toFailure(): ApiResult.Failure {
    val status = code()
    val retryAfter = response()?.headers()?.get("Retry-After")?.toLongOrNull()
    val body = try { response()?.errorBody()?.string() } catch (_: Throwable) { null }
    val parsed = parseErrorBody(body, status)
    return parsed.copy(retryAfterSeconds = parsed.retryAfterSeconds ?: retryAfter)
}

/** Parse an error envelope body if present, otherwise derive a code from the HTTP status. */
internal fun parseErrorBody(body: String?, status: Int): ApiResult.Failure {
    val parsedError: ApiError? = body?.takeIf { it.isNotBlank() }?.let {
        runCatching { errorJson.decodeFromString<Envelope<Unit>>(it).error }.getOrNull()
    }
    val code = parsedError?.code ?: codeForStatus(status)
    val message = parsedError?.message?.takeIf { it.isNotBlank() } ?: defaultMessageForStatus(status)
    return ApiResult.Failure(code = code, message = message, httpStatus = status)
}

private fun codeForStatus(status: Int): String = when (status) {
    400, 422 -> ErrorCodes.VALIDATION
    401 -> ErrorCodes.UNAUTHORIZED
    403 -> ErrorCodes.FORBIDDEN
    404 -> ErrorCodes.NOT_FOUND
    409 -> ErrorCodes.CONFLICT
    429 -> ErrorCodes.RATE_LIMITED
    in 500..599 -> ErrorCodes.SERVER_ERROR
    else -> ErrorCodes.UNKNOWN
}

private fun defaultMessageForStatus(status: Int): String = when (status) {
    400, 422 -> "The request was invalid."
    401 -> "Your session has expired. Please sign in again."
    403 -> "Access is not allowed."
    404 -> "Not found."
    409 -> "This action conflicts with the current state."
    429 -> "Too many requests. Please wait and try again."
    in 500..599 -> "The server encountered an error."
    else -> "Something went wrong."
}
