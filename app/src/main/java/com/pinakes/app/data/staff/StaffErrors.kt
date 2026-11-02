package com.pinakes.app.data.staff

import androidx.annotation.StringRes
import com.pinakes.app.R
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes

/**
 * The stable error codes of the staff 1.7.0 routes mapped to the app's own wording. Codes the
 * table does not know (notably the polymorphic 409 `conflict`, whose meaning only the server
 * message carries) return null: the caller shows the server message, else its fallback. Pure.
 */
object StaffErrors {

    @StringRes
    fun messageRes(code: String): Int? = when (code) {
        "not_eligible" -> R.string.staff_err_not_eligible
        "not_available" -> R.string.staff_err_not_available
        "max_loans_reached" -> R.string.staff_err_max_loans
        // Circulation rules of the borrower's patron type (Pinakes 0.9.0).
        "media_limit_reached" -> R.string.staff_err_media_limit
        "not_loanable" -> R.string.staff_err_not_loanable
        "reservations_disabled" -> R.string.staff_err_reservations_disabled
        "reservation_limit_reached" -> R.string.staff_err_reservation_limit
        "loan_closed" -> R.string.staff_err_loan_closed
        "concurrent_retry" -> R.string.staff_err_concurrent_retry
        "not_returnable" -> R.string.staff_err_not_returnable
        "max_renewals" -> R.string.staff_err_max_renewals
        "loan_overdue" -> R.string.staff_err_loan_overdue
        "loan_not_active" -> R.string.staff_err_loan_not_active
        "past_date" -> R.string.staff_err_past_date
        "ambiguous" -> R.string.staff_err_ambiguous
        "copy_on_loan" -> R.string.staff_err_copy_on_loan
        "copy_held" -> R.string.staff_err_copy_held
        "copy_in_circulation" -> R.string.staff_err_copy_in_circulation
        "copy_has_history" -> R.string.staff_err_copy_has_history
        "no_copies" -> R.string.staff_err_no_copies
        "offer_closed" -> R.string.staff_err_offer_closed
        "out_of_order" -> R.string.staff_err_out_of_order
        "job_busy" -> R.string.staff_err_job_busy
        "gone" -> R.string.staff_err_gone
        "upload_rejected" -> R.string.staff_err_upload_rejected
        "cover_download_failed" -> R.string.staff_err_cover_download_failed
        SubmissionKeys.CONFLICT -> R.string.staff_err_idempotency_conflict
        "feature_disabled" -> R.string.staff_err_feature_disabled
        ErrorCodes.RATE_LIMITED -> R.string.staff_err_rate_limited
        ErrorCodes.NETWORK -> R.string.offline_generic
        else -> null
    }

    /**
     * The resource for [failure], when its code is one of ours. A 409 `not_eligible` carries the
     * core reason in `meta.reason` (`debt_block`, `card_expired`, `user_suspended`): the precise
     * wording when we know it, the generic one otherwise.
     */
    @StringRes
    fun messageRes(failure: ApiResult.Failure): Int? =
        if (failure.code == "not_eligible") eligibilityRes(failure.meta?.reason) ?: messageRes(failure.code)
        else messageRes(failure.code)

    /**
     * Wording for an `eligibility_reason` (user lists, loan create) or a `not_eligible` reason;
     * null for a reason this app version does not know (the caller shows the raw value).
     */
    @StringRes
    fun eligibilityRes(reason: String?): Int? = when (reason) {
        "debt_block" -> R.string.staff_err_debt_block
        "card_expired" -> R.string.staff_err_card_expired
        "user_suspended" -> R.string.staff_err_user_suspended
        else -> null
    }
}
