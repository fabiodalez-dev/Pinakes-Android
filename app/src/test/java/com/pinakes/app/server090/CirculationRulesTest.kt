package com.pinakes.app.server090

import com.pinakes.app.R
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.parseErrorBody
import com.pinakes.app.ui.screens.detail.CirculationRules
import com.pinakes.app.ui.screens.detail.LoanAction
import com.pinakes.app.ui.screens.detail.ViewerRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Patron-type rules on the book page and the 0.9.0 refusal codes of POST /reservations. */
class CirculationRulesTest {

    private fun refusal(code: String, message: String = "Server text", reason: String? = null, status: Int = 409): ApiResult.Failure {
        val meta = reason?.let { """{"reason":"$it"}""" } ?: "{}"
        return parseErrorBody("""{"data":null,"meta":$meta,"error":{"code":"$code","message":"$message"}}""", status)
    }

    @Test fun unknownRulesKeepTheUsualButton() {
        assertEquals(LoanAction.Request, CirculationRules.loanAction(available = true, rules = null))
        assertEquals(LoanAction.Reserve, CirculationRules.loanAction(available = false, rules = null))
    }

    @Test fun consultationOnlyWinsWhateverTheCopies() {
        val rules = ViewerRules(loanable = false)
        assertEquals(LoanAction.ConsultationOnly, CirculationRules.loanAction(true, rules))
        assertEquals(LoanAction.ConsultationOnly, CirculationRules.loanAction(false, rules))
        assertFalse(CirculationRules.canRequest(LoanAction.ConsultationOnly))
    }

    @Test fun noReservationsOnlyMattersWhenNoCopyIsFree() {
        val rules = ViewerRules(reservationsEnabled = false)
        assertEquals(LoanAction.Request, CirculationRules.loanAction(true, rules))
        assertEquals(LoanAction.ReservationsDisabled, CirculationRules.loanAction(false, rules))
        assertFalse(CirculationRules.canRequest(LoanAction.ReservationsDisabled))
        assertTrue(CirculationRules.canRequest(LoanAction.Reserve))
    }

    @Test fun rulesAreReadOffTheAvailabilityPayload() {
        assertEquals(ViewerRules(false, true), ViewerRules.of(AvailabilityCalendar(loanable = false)))
        assertEquals(ViewerRules(true, true), ViewerRules.of(AvailabilityCalendar()))
    }

    @Test fun aRefusalTeachesTheRuleSoTheButtonChangesAtOnce() {
        assertEquals(ViewerRules(loanable = false), CirculationRules.rulesAfterRefusal(null, "not_loanable"))
        assertEquals(ViewerRules(reservationsEnabled = false), CirculationRules.rulesAfterRefusal(ViewerRules(), "reservations_disabled"))
        assertNull(CirculationRules.rulesAfterRefusal(null, "not_available"))
    }

    @Test fun theNewConflictCodesHaveTheirOwnWording() {
        assertEquals(R.string.request_err_not_loanable, CirculationRules.requestErrorRes(refusal("not_loanable")))
        assertEquals(R.string.request_err_reservations_disabled, CirculationRules.requestErrorRes(refusal("reservations_disabled")))
        assertEquals(R.string.request_err_reservation_limit, CirculationRules.requestErrorRes(refusal("reservation_limit_reached")))
        assertEquals(R.string.request_err_media_limit, CirculationRules.requestErrorRes(refusal("media_limit_reached")))
        assertEquals(R.string.request_err_max_loans, CirculationRules.requestErrorRes(refusal("max_loans_reached")))
    }

    @Test fun notEligibleNamesTheReasonWhenTheServerGivesIt() {
        assertEquals(R.string.request_err_debt_block, CirculationRules.requestErrorRes(refusal("not_eligible", reason = "debt_block")))
        assertEquals(R.string.request_err_card_expired, CirculationRules.requestErrorRes(refusal("not_eligible", reason = "card_expired")))
        assertEquals(R.string.request_err_suspended, CirculationRules.requestErrorRes(refusal("not_eligible", reason = "user_suspended")))
    }

    @Test fun notEligibleWithoutAReasonKeepsTheServerSentence() {
        // The patron route answers 403 not_eligible with the cause in the message only.
        assertNull(CirculationRules.requestErrorRes(refusal("not_eligible", "Prestiti bloccati per debito.", status = 403)))
        assertNull(CirculationRules.requestErrorRes(refusal("not_eligible", reason = "a_reason_this_app_does_not_know")))
    }

    @Test fun unknownCodesAndNotAvailableFallBackToTheServerMessage() {
        assertNull(CirculationRules.requestErrorRes(refusal("some_future_code")))
        assertNull(CirculationRules.requestErrorRes(refusal("not_available", "Durata massima del prestito: 4 giorni")))
        assertNull(CirculationRules.requestErrorRes(refusal("duplicate")))
    }

    @Test fun aBare409FromAProxyStillReadsAsADateClash() {
        val failure = parseErrorBody("<html>conflict</html>", 409)
        assertEquals(R.string.snackbar_request_conflict, CirculationRules.requestErrorRes(failure))
    }
}
