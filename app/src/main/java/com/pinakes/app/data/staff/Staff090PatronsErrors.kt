package com.pinakes.app.data.staff

import androidx.annotation.StringRes
import com.pinakes.app.R
import com.pinakes.app.data.network.ApiResult

/**
 * The error codes of the Pinakes 0.9.0 patron tools mapped to the app's wording, checked before
 * [StaffErrors]. Unknown codes return null: the caller shows the server message (a 422
 * `validation` always carries the precise one). Pure.
 */
object Staff090PatronsErrors {

    @StringRes
    fun messageRes(failure: ApiResult.Failure): Int? = when (failure.code) {
        "card_not_renewable" -> R.string.staff090_patrons_err_card_not_renewable
        "amount_exceeds_balance" -> R.string.staff090_patrons_err_amount_exceeds_balance
        "card_not_found" -> R.string.staff090_patrons_err_card_not_found
        "not_started" -> R.string.staff090_patrons_err_not_started
        "already_started" -> R.string.staff090_patrons_err_already_started
        "job_cancelled" -> R.string.staff090_patrons_err_job_cancelled
        "file_missing" -> R.string.staff090_patrons_err_file_missing
        "import_not_finished" -> R.string.staff090_patrons_err_import_not_finished
        // The visitor log answers 503 not_available until the 0.9.0 database update has run;
        // the copy desk's 409 not_available means something else and keeps its wording.
        // 403 not_permitted: ONE action refused to a Staff account that keeps its role (another
        // account's patron record, administrator-only settings). The server's message says why;
        // unlike `forbidden` it never drops the staff role (isForbidden() is false for it).
        "not_permitted" -> null
        "not_available" -> if (failure.httpStatus == 503) R.string.staff090_patrons_err_db_update_pending else null
        else -> null
    }
}
