package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Pinakes 0.9.0 patron tools (Mobile API 1.10.0) against bodies captured from a real
 * 0.9.0-rc.1 server (fixtures `s090_patrons_*`): the patron section and the ledger, the return
 * with a waived late fine, the visitor log desk and the patron CSV import.
 */
class Staff090PatronsFlowTest : AppFlowTest() {

    private val health = "s090_patrons_health"

    private fun desk() = launch(health = health) {
        json("GET staff/circulation/summary", "s090_patrons_summary")
        json("GET staff/loans", "s090_patrons_loans_overdue")
        json("GET staff/loans/98229", "s090_patrons_loan_overdue")
        json("POST staff/loans/98229/return", "s090_patrons_return_waived")
        json("GET staff/users/113146", "s090_patrons_user")
        sequence("GET staff/users/113146/ledger", 200 to "s090_patrons_ledger", 200 to "s090_patrons_ledger_after")
        json("POST staff/users/113146/ledger/payments", "s090_patrons_payment", status = 201)
    }

    private fun openOverdueLoan() {
        openStaffHub()
        click(text(R.string.staff_circulation_desk))
        clickTag("counter-overdue")
        click("Patroncap fine book")
        waitText("LIB-343656-C1")
    }

    @Test fun theReturnShowsTheLateFineAndSendsTheWaiver() {
        desk()
        openOverdueLoan()
        clickTag("action-return")
        waitText(text(R.string.staff_return_title))
        waitFor(androidx.compose.ui.test.hasTestTag("return-fine"))
        click(text(R.string.staff090_patrons_return_waive))
        clickTag("return-confirm")
        compose.waitForIdle()
        // No reason yet: nothing sent.
        assertTrue(api.recorded("POST", "staff/loans/98229/return").isEmpty())
        typeIntoTag("return-waive-reason", "First time")
        clickTag("return-confirm")
        val body = lastBody("POST", "staff/loans/98229/return")
        assertTrue(body, body.contains("\"waive_fine\":true"))
        assertTrue(body, body.contains("\"waive_reason\":\"First time\""))
        waitText(text(R.string.staff_loan_returned))
    }

    @Test fun theBorrowerShowsTheBalanceAndAPaymentIsRecordedOnce() {
        desk()
        openOverdueLoan()
        click(text(R.string.staff_user_open))
        waitFor(androidx.compose.ui.test.hasTestTag("patron-section"))
        waitText("E2E Fines")
        click(text(R.string.staff090_ledger))
        waitRequest("GET", "staff/users/113146/ledger")
        waitText("Lost library card")
        waitText("Patroncap fine book")
        clickTag("ledger-payment")
        clickTag("method-pos")
        clickTag("ledger-submit")
        waitRequest("POST", "staff/users/113146/ledger/payments")
        val sent = api.recorded("POST", "staff/users/113146/ledger/payments").last()
        val body = sent.body.clone().readUtf8()
        // The amount field starts from the whole balance (12.50): exact cents, no float.
        assertTrue(body, body.contains("\"amount_cents\":1250"))
        assertTrue(body, body.contains("\"method\":\"pos\""))
        assertNotNull(sent.getHeader("Idempotency-Key"))
        assertEquals(1, api.recorded("POST", "staff/users/113146/ledger/payments").size)
        waitText(text(R.string.staff090_patrons_payment_recorded))
        waitFor(androidx.compose.ui.test.hasTestTag("receipt-19"))
    }

    @Test fun theDeskRecordsACardAndSaysWhenItIsUnknown() {
        launch(health = health) {
            json("GET staff/attendance", "s090_patrons_att_overview")
            sequence(
                "POST staff/attendance/check-ins",
                201 to "s090_patrons_att_checkin",
                404 to "s090_patrons_att_unknown",
            )
            json("POST staff/attendance/guests", "s090_patrons_att_guest", status = 201)
        }
        openStaffHub()
        scrollListTo(text(R.string.staff090_attendance))
        click(text(R.string.staff090_attendance))
        denyCameraPromptIfShown()
        typeIntoTag("card-code-input", "  PCAP090B ")
        clickDescription(text(R.string.staff090_patrons_check_in))
        val first = lastBody("POST", "staff/attendance/check-ins")
        assertTrue(first, first.contains("\"card_code\":\"PCAP090B\""))
        assertNotNull(api.recorded("POST", "staff/attendance/check-ins").last().getHeader("Idempotency-Key"))
        waitFor(androidx.compose.ui.test.hasTestTag("visit-welcome"))
        waitText("Marta P.")

        typeIntoTag("card-code-input", "NOPE-PCAP")
        clickDescription(text(R.string.staff090_patrons_check_in))
        waitFor(androidx.compose.ui.test.hasTestTag("visit-unknown_card"))

        clickTag("guest-open")
        typeIntoTag("guest-name", "Visiting pcap")
        typeIntoTag("guest-reason", "Research")
        clickTag("guest-save")
        val guest = lastBody("POST", "staff/attendance/guests")
        assertTrue(guest, guest.contains("\"name\":\"Visiting pcap\""))
        waitFor(androidx.compose.ui.test.hasTestTag("visit-guest"))
    }

    @Test fun anAdministratorOnlyActionRefusedKeepsTheStaffArea() {
        launch(role = "staff", health = health) {
            json("GET staff/attendance", "s090_patrons_att_overview")
            json("POST staff/attendance/purge", "s090_patrons_np_purge", status = 403)
        }
        openStaffHub()
        scrollListTo(text(R.string.staff090_attendance))
        click(text(R.string.staff090_attendance))
        denyCameraPromptIfShown()
        clickTag("tab-stats")
        // The captured overview is an administrator's: the purge is offered.
        clickTag("attendance-purge")
        clickInDialog(text(R.string.staff090_patrons_purge))
        waitText("Operation reserved for administrators.")
        // 403 not_permitted never drops the role: still on the screen, still staff.
        assertFalse(api.recorded("POST", "staff/attendance/purge").isEmpty())
        waitFor(androidx.compose.ui.test.hasTestTag("tab-desk"))
        assertEquals("staff", deps.session().role.value)
    }

    @Test fun aPatronCsvIsUploadedMappedAndImported() {
        val name = "e2e-patrons-${System.currentTimeMillis() % 100000}.csv"
        val uri = publishDownload(name, "Nome;Cognome;Email;Tessera\nLuca;Pcapimport;pcapimp1b_090@example.test;PCAPIMP1B\nNoMail;Pcapimport;;PCAPIMP3B\n")
        try {
            launch(health = health) {
                json("POST staff/user-imports", "s090_patrons_ui_upload", status = 201)
                routeJobs()
            }
            openStaffHub()
            scrollListTo(text(R.string.staff090_user_import))
            click(text(R.string.staff090_user_import))
            click(text(R.string.staff_import_choose_file))
            pickInDocumentsUi(name)
            waitText(name, timeoutMs = 15_000)
            clickTag("user-import-upload")
            val upload = lastBody("POST", "staff/user-imports")
            assertTrue(upload, upload.contains("filename=\"$name\""))
            assertTrue(upload, upload.contains("name=\"delimiter\""))
            waitFor(androidx.compose.ui.test.hasTestTag("user-import-preview"))
            clickTag("user-import-start")
            val start = lastBody("POST", "staff/user-imports/${JOB}/start")
            assertTrue(start, start.contains("\"mapping\":[\"nome\",\"cognome\",\"email\",\"codice_tessera\"]"))
            assertTrue(start, start.contains("\"send_invites\":false"))
            assertEquals("""{"start":0}""", lastBody("POST", "staff/user-imports/${JOB}/chunk"))
            waitText(text(R.string.staff_import_status_done), timeoutMs = 20_000)
            waitFor(androidx.compose.ui.test.hasTestTag("user-import-errors"))
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun FakePinakes.routeJobs() {
        json("POST staff/user-imports/$JOB/start", "s090_patrons_ui_start")
        json("POST staff/user-imports/$JOB/chunk", "s090_patrons_ui_chunk")
        json("GET staff/user-imports/$JOB", "s090_patrons_ui_chunk")
    }

    private companion object {
        const val JOB = "74151523-1959-4ac8-b635-0bd862ae9a27"
    }
}
