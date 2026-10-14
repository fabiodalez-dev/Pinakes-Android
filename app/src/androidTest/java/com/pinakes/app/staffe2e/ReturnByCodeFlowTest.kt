package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hub → Scan a copy → type the inventory code → the copy and its loan → Return. */
class ReturnByCodeFlowTest : AppFlowTest() {

    @Test fun typedCopyCodeFindsTheLoanAndReturnsIt() {
        launch {
            json("GET staff/copies/by-code", "copy_by_code_found")
            json("POST staff/loans/return-by-code", "staff_loan_7_returned")
        }
        openStaffHub()
        click(text(R.string.staff_scan_copy))
        denyCameraPromptIfShown()
        typeInto(text(R.string.staff_copy_code), "  INV-0011 ")
        clickDescription(text(R.string.staff_scan_lookup))
        waitRequest("GET", "staff/copies/by-code")
        assertEquals("INV-0011", api.recorded("GET", "staff/copies/by-code").last().requestUrl?.queryParameter("code"))
        waitText("Ada Rossi · C-0012")
        clickTag("copy-return")
        clickTag("return-confirm")
        val body = lastBody("POST", "staff/loans/return-by-code")
        assertTrue(body, body.contains("\"code\":\"INV-0011\""))
        assertTrue(body, body.contains("\"outcome\":\"restituito\""))
        waitText(text(R.string.staff_copy_returned_count, 1))
    }

    @Test fun anUnknownCodeSaysSo() {
        launch { json("GET staff/copies/by-code", "copy_by_code_missing") }
        openStaffHub()
        click(text(R.string.staff_scan_copy))
        denyCameraPromptIfShown()
        typeInto(text(R.string.staff_copy_code), "NOPE-1")
        clickDescription(text(R.string.staff_scan_lookup))
        waitText(text(R.string.staff_copy_not_found))
        waitText(text(R.string.staff_copy_not_found_body, "NOPE-1"))
    }
}
