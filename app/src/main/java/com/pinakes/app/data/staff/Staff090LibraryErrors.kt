package com.pinakes.app.data.staff

import androidx.annotation.StringRes
import com.pinakes.app.R
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes

/**
 * Error codes of the library settings areas (Mobile API 1.10.0): settings, patron types and
 * rules, closures calendar. Codes it does not know return null and go on to
 * [StaffErrors] / the server message. Pure.
 */
object Staff090LibraryErrors {

    @StringRes
    fun messageRes(code: String): Int? = when (code) {
        "calendar_always_closed" -> R.string.staff090_library_err_always_closed
        "preset_closure" -> R.string.staff090_library_err_preset_closure
        "schema_not_ready" -> R.string.staff090_library_err_schema_not_ready
        "file_too_large" -> R.string.staff090_library_err_ics_too_large
        else -> null
    }

    /**
     * True when the failure means this screen is not for this user or this server: an
     * administrator-only area answered 403, or the module is off for Staff (404
     * `feature_disabled`). The screen leaves, showing the server's message.
     */
    fun closesScreen(failure: ApiResult.Failure): Boolean =
        (failure.httpStatus == 403 || failure.code == ErrorCodes.FORBIDDEN) && failure.fromEnvelope ||
            failure.code == "feature_disabled"
}
