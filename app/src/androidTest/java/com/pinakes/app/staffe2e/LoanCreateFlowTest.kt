package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hub → New loan: search the borrower, pick the book, hand over now, then the loan opens. */
class LoanCreateFlowTest : AppFlowTest() {

    private fun setUp() = launch {
        json("GET staff/users", "staff_users")
        json("GET staff/books/5/availability", "staff_book_availability")
        json("POST staff/loans", "staff_loan_created", status = 201)
        json("GET staff/loans/21", "staff_loan_created")
    }

    private fun fillForm() {
        openStaffHub()
        click(text(R.string.staff_loan_new))
        click(text(R.string.staff_pick_user_placeholder))
        typeIntoTag("picker-search", "Ada")
        click("Ada Rossi")
        click(text(R.string.staff_pick_book_placeholder))
        typeIntoTag("picker-search", "Orl")
        click("Orlando")
        waitRequest("GET", "staff/books/5/availability")
    }

    @Test fun createAnImmediateLoanSendsTheFormAndAnIdempotencyKey() {
        setUp()
        fillForm()
        assertEquals("3", api.recorded("GET", "staff/books/5/availability").last().requestUrl?.queryParameter("user_id"))
        click(text(R.string.staff_loan_create))
        waitRequest("POST", "staff/loans")
        val request = api.recorded("POST", "staff/loans").last()
        val body = request.body.clone().readUtf8()
        assertTrue(body, body.contains("\"user_id\":3"))
        assertTrue(body, body.contains("\"book_id\":5"))
        assertTrue(body, body.contains("\"immediate\":true"))
        val key = request.getHeader("Idempotency-Key")
        assertEquals(36, key?.length)
        // The created loan opens.
        waitRequest("GET", "staff/loans/21")
        waitText("INV-0011")
    }

    @Test fun anIneligibleBorrowerIsFlaggedAndTheServerRefusalShown() {
        setUp()
        api.body("POST staff/loans", 409, """{"data":null,"meta":{"reason":"user_suspended"},"error":{"code":"not_eligible","message":"x"}}""")
        openStaffHub()
        click(text(R.string.staff_loan_new))
        click(text(R.string.staff_pick_user_placeholder))
        typeIntoTag("picker-search", "Bru")
        waitText("Suspended")
        click("Bruno Sospeso")
        click(text(R.string.staff_pick_book_placeholder))
        typeIntoTag("picker-search", "Orl")
        click("Orlando")
        click(text(R.string.staff_loan_create))
        waitText(text(R.string.staff_err_not_eligible))
    }
}
