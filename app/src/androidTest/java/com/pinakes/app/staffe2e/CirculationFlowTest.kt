package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hub → Circulation desk → counters → On loan → a loan → Return as damaged with a penalty. */
class CirculationFlowTest : AppFlowTest() {

    private fun desk() = launch {
        json("GET staff/circulation/summary", "circulation_summary")
        json("GET staff/loans", "staff_loans_active")
        json("GET staff/loans/7", "staff_loan_7")
        json("POST staff/loans/7/return", "staff_loan_7_returned")
        json("GET staff/users/3", "staff_user_3")
    }

    @Test fun returnALoanAsDamagedWithAPenalty() {
        desk()
        openStaffHub()
        click(text(R.string.staff_circulation_desk))
        waitText(text(R.string.staff_circulation_today, 1, 2))
        clickTag("counter-active")
        waitRequest("GET", "staff/loans")
        assertEquals("active", api.recorded("GET", "staff/loans").last().requestUrl?.queryParameter("bucket"))
        click("Orlando")
        waitText("INV-0011")
        clickTag("action-return")
        waitText(text(R.string.staff_return_title))
        click(text(R.string.staff_return_outcome_damaged))
        typeInto(text(R.string.staff_penalty), "12,50")
        clickTag("return-confirm")
        val body = lastBody("POST", "staff/loans/7/return")
        assertTrue(body, body.contains("\"outcome\":\"danneggiato\""))
        assertTrue(body, body.contains("\"penalty\":12.5"))
        waitText(text(R.string.staff_loan_returned))
    }

    @Test fun anInvalidPenaltyIsNotSent() {
        desk()
        openStaffHub()
        click(text(R.string.staff_circulation_desk))
        clickTag("counter-active")
        click("Orlando")
        clickTag("action-return")
        click(text(R.string.staff_return_outcome_lost))
        typeInto(text(R.string.staff_penalty), "12.345")
        waitText(text(R.string.staff_penalty_invalid))
        clickTag("return-confirm")
        compose.waitForIdle()
        assertTrue(api.recorded("POST", "staff/loans/7/return").isEmpty())
    }

    @Test fun theBorrowerOpensFromTheLoan() {
        desk()
        openStaffHub()
        click(text(R.string.staff_circulation_desk))
        clickTag("counter-active")
        click("Orlando")
        click(text(R.string.staff_user_open))
        waitText("C-0012")
        waitText(text(R.string.staff_user_open_loans).uppercase())
    }

    @Test fun aServerConflictShowsTheServerMessage() {
        desk()
        api.body("POST staff/loans/7/renew", 409, """{"data":null,"meta":{},"error":{"code":"max_renewals","message":"x"}}""")
        openStaffHub()
        click(text(R.string.staff_circulation_desk))
        clickTag("counter-active")
        click("Orlando")
        clickTag("action-renew")
        clickInDialog(text(R.string.staff_loan_action_renew))
        waitText(text(R.string.staff_err_max_renewals))
    }
}
