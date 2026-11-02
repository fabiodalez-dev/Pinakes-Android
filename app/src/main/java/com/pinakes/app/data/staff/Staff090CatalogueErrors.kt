package com.pinakes.app.data.staff

import androidx.annotation.StringRes
import com.pinakes.app.R
import com.pinakes.app.data.network.ApiResult

/**
 * Error codes of the 0.9.0 cataloguing routes in the app's own words. Checked before
 * [StaffErrors]; a code neither table knows (`validation`, whose server text names the field)
 * shows the server message. Pure.
 */
object Staff090CatalogueErrors {

    /**
     * [copyCataloguing]: the failure comes from a copy cataloguing call, where 410 `gone` means
     * the stored search expired (on a MARC chunk it keeps the import wording of [StaffErrors]).
     */
    @StringRes
    fun messageRes(failure: ApiResult.Failure, copyCataloguing: Boolean = false): Int? = when (failure.code) {
        "schema_missing" -> R.string.staff090_catalogue_err_schema_missing
        "copy_cataloging_unavailable" -> R.string.staff090_catalogue_err_copy_unavailable
        "job_cancelled" -> R.string.staff090_catalogue_err_job_cancelled
        "gone" -> if (copyCataloguing) R.string.staff090_catalogue_err_search_gone else null
        // 403 `not_permitted`: one action refused to a staff account that keeps its role. Only
        // the server knows which permission is missing, so its text is shown; unlike
        // `forbidden`, it never drops the stored role (staffCall checks `forbidden` alone).
        "not_permitted" -> null
        else -> null
    }

    /** The unavailability reason of `copy_cataloging` (`plugin_inactive`), or a generic text. */
    @StringRes
    fun copyCatalogingReasonRes(reason: String?): Int = when (reason) {
        "plugin_inactive" -> R.string.staff090_catalogue_copy_reason_plugin
        else -> R.string.staff090_catalogue_err_copy_unavailable
    }
}
