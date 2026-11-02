package com.pinakes.app.ui.screens.detail

import androidx.annotation.StringRes
import com.pinakes.app.R
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes

/**
 * What the reader's patron type allows on a title (Pinakes 0.9.0 circulation rules), read off
 * `GET /catalog/books/{id}/availability`. Both default to the pre-0.9.0 behaviour, so an older
 * server, or a page whose availability was not fetched, keeps the usual button.
 */
data class ViewerRules(
    /** False: the title is consultation only for this reader. */
    val loanable: Boolean = true,
    /** False: the reader's patron type cannot join a waitlist. */
    val reservationsEnabled: Boolean = true,
) {
    companion object {
        fun of(calendar: AvailabilityCalendar): ViewerRules =
            ViewerRules(loanable = calendar.loanable, reservationsEnabled = calendar.reservationsEnabled)
    }
}

/** The loan button on the book page. */
enum class LoanAction {
    /** A copy is free now: "Request loan". */
    Request,

    /** No copy is free: "Reserve" (joins the waitlist). */
    Reserve,

    /** Consultation only for this reader: no request, a disabled button that says so. */
    ConsultationOnly,

    /** No copy is free and the reader's patron type has no reservations: nothing to ask for. */
    ReservationsDisabled,
}

object CirculationRules {

    /** The button for a title that is [available] now, under [rules] (null = not known yet). */
    fun loanAction(available: Boolean, rules: ViewerRules?): LoanAction = when {
        rules?.loanable == false -> LoanAction.ConsultationOnly
        available -> LoanAction.Request
        rules?.reservationsEnabled == false -> LoanAction.ReservationsDisabled
        else -> LoanAction.Reserve
    }

    /** True when the button opens the request sheet; false when it is shown disabled with a reason. */
    fun canRequest(action: LoanAction): Boolean = action == LoanAction.Request || action == LoanAction.Reserve

    /** What a refusal teaches about the reader's rules: the button then shows the reason at once. */
    fun rulesAfterRefusal(current: ViewerRules?, code: String): ViewerRules? = when (code) {
        "not_loanable" -> (current ?: ViewerRules()).copy(loanable = false)
        "reservations_disabled" -> (current ?: ViewerRules()).copy(reservationsEnabled = false)
        else -> current
    }

    /**
     * Our wording for a refused POST /reservations, or null when the server's own message is the
     * better text (an unknown code, or `not_available` with the longest loan still possible).
     * `not_eligible` names its cause only when the server passes `meta.reason`; without it the
     * server message (which says whether the card, the account or the balance is the reason) wins.
     */
    @StringRes
    fun requestErrorRes(failure: ApiResult.Failure): Int? = when (failure.code) {
        "not_loanable" -> R.string.request_err_not_loanable
        "reservations_disabled" -> R.string.request_err_reservations_disabled
        "reservation_limit_reached" -> R.string.request_err_reservation_limit
        "media_limit_reached" -> R.string.request_err_media_limit
        "max_loans_reached" -> R.string.request_err_max_loans
        "not_eligible" -> when (failure.meta?.reason) {
            "debt_block" -> R.string.request_err_debt_block
            "card_expired" -> R.string.request_err_card_expired
            "user_suspended" -> R.string.request_err_suspended
            else -> null
        }
        // The generic 409 without a server code (a proxy page): the dates clash.
        ErrorCodes.CONFLICT -> R.string.snackbar_request_conflict
        else -> null
    }
}
