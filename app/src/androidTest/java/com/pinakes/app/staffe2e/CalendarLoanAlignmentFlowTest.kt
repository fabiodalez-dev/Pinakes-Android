package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Native desk follows the branch-calendar facts returned by the server. */
class CalendarLoanAlignmentFlowTest : AppFlowTest() {
    private fun loan(status: String, overdue: Boolean, actions: String) = """{
        "id":7,"status":"$status","is_overdue":$overdue,
        "book":{"id":11,"title":"Calendar-aligned loan"},
        "copy":{"id":1,"inventory_number":"INV-CALENDAR"},
        "user":{"id":3,"name":"Calendar reader"},
        "due_at":"2030-12-25","effective_due_at":"2030-12-27",
        "renewals":0,"max_renewals":3,"days_overdue":${if (overdue) 1 else 0},
        "actions":$actions
    }"""

    private fun desk(payload: String) {
        launch {
            json("GET staff/circulation/summary", "circulation_summary")
            body("GET staff/loans", 200, """{"data":[$payload],"meta":{"count":1,"has_more":false},"error":null}""")
            body("GET staff/loans/7", 200, """{"data":$payload,"meta":{},"error":null}""")
            body("POST staff/loans/7/renew", 200, """{"data":$payload,"meta":{},"error":null}""")
        }
        openStaffHub()
        click(text(R.string.staff_circulation_desk))
        clickTag("counter-active")
        click("Calendar-aligned loan")
        waitText("INV-CALENDAR")
    }

    @Test fun firstOpenDayOffersRenewalAndSendsItToTheServer() {
        desk(loan("in_ritardo", false, """["return","renew","edit","receipt"]"""))
        assertFalse(exists(text(R.string.staff_loan_action_recall)))
        clickTag("action-renew")
        clickInDialog(text(R.string.staff_loan_action_renew))
        waitRequest("POST", "staff/loans/7/renew")
        assertTrue(api.recorded("POST", "staff/loans/7/renew").size == 1)
    }

    @Test fun genuinelyLateLoanOffersRecallAndNoRenewal() {
        desk(loan("in_corso", true, """["return","recall","edit","receipt"]"""))
        waitText(text(R.string.staff_loan_action_recall))
        assertFalse(exists(text(R.string.staff_loan_action_renew)))
        assertTrue(api.recorded("POST", "staff/loans/7/renew").isEmpty())
    }
}
