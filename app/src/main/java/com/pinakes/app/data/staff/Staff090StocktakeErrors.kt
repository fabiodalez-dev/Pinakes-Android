package com.pinakes.app.data.staff

import androidx.annotation.StringRes
import com.pinakes.app.R
import com.pinakes.app.data.network.ApiResult

/**
 * Error codes of the stocktake and statistics areas (Mobile API 1.10.0). Codes it does not
 * know return null and go on to [StaffErrors] / the server message. Pure.
 */
object Staff090StocktakeErrors {

    /** 403: this staff account may not do this one action (closing with `lost`); the role stays. */
    const val NOT_PERMITTED = "not_permitted"
    const val SESSION_CLOSED = "session_closed"
    const val SESSION_CANCELLED = "session_cancelled"
    const val REPORT_TOO_LARGE = "report_too_large"

    @StringRes
    fun messageRes(code: String): Int? = when (code) {
        SESSION_CLOSED -> R.string.staff090_stocktake_err_session_closed
        SESSION_CANCELLED -> R.string.staff090_stocktake_err_session_cancelled
        REPORT_TOO_LARGE -> R.string.staff090_stocktake_err_report_too_large
        "invalid_state" -> R.string.staff090_stocktake_err_invalid_state
        else -> null
    }

    /** The server explains `not_permitted` itself (who may do it): its message is shown. */
    fun isNotPermitted(failure: ApiResult.Failure): Boolean = failure.fromEnvelope && failure.code == NOT_PERMITTED

    /** The session stopped being open meanwhile (another operator closed or cancelled it): reload it. */
    fun sessionEnded(failure: ApiResult.Failure): Boolean =
        failure.fromEnvelope && (failure.code == SESSION_CLOSED || failure.code == SESSION_CANCELLED)

    /** Module switched off on the server while the screen was open: 404 `feature_disabled`. */
    fun moduleOff(failure: ApiResult.Failure): Boolean = failure.fromEnvelope && failure.code == "feature_disabled"
}
