package com.pinakes.app.data.staff

import androidx.annotation.StringRes
import com.pinakes.app.R
import com.pinakes.app.data.network.ApiResult

/**
 * Error codes of the library settings areas (Mobile API 1.10.0): settings, patron types and
 * rules, closures calendar. Codes it does not know return null and go on to
 * [StaffErrors] / the server message. Pure.
 */
object Staff090LibraryErrors {

    /** 403: this staff account may not do this one thing (an administrator may); the role stays. */
    const val NOT_PERMITTED = "not_permitted"

    @StringRes
    fun messageRes(code: String): Int? = when (code) {
        "calendar_always_closed" -> R.string.staff090_library_err_always_closed
        "preset_closure" -> R.string.staff090_library_err_preset_closure
        "schema_not_ready" -> R.string.staff090_library_err_schema_not_ready
        "file_too_large" -> R.string.staff090_library_err_ics_too_large
        else -> null
    }

    /**
     * The server explains `not_permitted` itself (which role or permission it takes): its
     * message is shown, our generic wording only when it sent none.
     */
    fun isNotPermitted(failure: ApiResult.Failure): Boolean = failure.fromEnvelope && failure.code == NOT_PERMITTED

    /**
     * True when the failure of a screen's first load means the screen is not for this user or
     * this server: the area refused this account (`not_permitted`) or the module is off for
     * Staff (404 `feature_disabled`). The screen closes with the server's message; the staff
     * area stays. A refused action inside a screen only shows the message.
     */
    fun closesScreen(failure: ApiResult.Failure): Boolean =
        failure.fromEnvelope && (failure.code == NOT_PERMITTED || failure.code == "feature_disabled")
}
