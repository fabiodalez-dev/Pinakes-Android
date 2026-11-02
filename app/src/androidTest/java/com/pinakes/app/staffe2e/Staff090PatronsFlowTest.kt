package com.pinakes.app.staffe2e

import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
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
        // The description is part of the row's detail line ("Lost library card · date").
        waitFor(androidx.compose.ui.test.hasText("Lost library card", substring = true))
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

    /**
     * Type [code] in the desk field and send it with the keyboard's Done, as a wedge reader does,
     * until the server has seen [posts] check-ins. The field sits under imePadding: while the
     * keyboard animates in (seconds on a loaded emulator) the whole column moves, so a tap on
     * the field's icon computed from the old layout can land beside it and send nothing.
     */
    private fun checkInAtDesk(code: String, posts: Int) {
        val field = androidx.compose.ui.test.hasSetTextAction() and androidx.compose.ui.test.hasTestTag("card-code-input")
        repeat(3) {
            if (api.recorded("POST", "staff/attendance/check-ins").size >= posts) return@repeat
            runCatching {
                waitFor(field)
                compose.onAllNodes(field).onFirst().performTextReplacement(code)
                compose.onAllNodes(field).onFirst().performImeAction()
                compose.waitUntil(5_000) { api.recorded("POST", "staff/attendance/check-ins").size >= posts }
            }
        }
        assertEquals(posts, api.recorded("POST", "staff/attendance/check-ins").size)
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
        click(text(R.string.staff090_attendance))
        denyCameraPromptIfShown()
        checkInAtDesk("  PCAP090B ", posts = 1)
        val first = lastBody("POST", "staff/attendance/check-ins")
        assertTrue(first, first.contains("\"card_code\":\"PCAP090B\""))
        assertNotNull(api.recorded("POST", "staff/attendance/check-ins").last().getHeader("Idempotency-Key"))
        waitFor(androidx.compose.ui.test.hasTestTag("visit-welcome"))
        waitText("Marta P.")

        checkInAtDesk("NOPE-PCAP", posts = 2)
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
            // The app refreshes the role from /me: a Staff account's own answer.
            json("GET me", "me_staff")
            json("GET staff/attendance", "s090_patrons_att_overview")
            json("POST staff/attendance/purge", "s090_patrons_np_purge", status = 403)
        }
        openStaffHub()
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

    @Test fun anImportLeftOpenElsewhereIsResumedOrCancelled() {
        launch(health = health) {
            sequence("GET staff/user-imports", 200 to "s090_patrons_ui_list", 200 to "s090_patrons_ui_list_after")
            json("GET staff/user-imports/$READY", "s090_patrons_ui_job_ready")
            json("DELETE staff/user-imports/$STARTED", "s090_patrons_ui_cancelled")
        }
        openStaffHub()
        click(text(R.string.staff090_user_import))
        waitText("readers-b.csv")
        waitText(text(R.string.staff090_patrons_import_open_not_started, 2))
        // Cancel the started one without opening it: the list is read again.
        clickTag("open-cancel-$STARTED")
        waitRequest("DELETE", "staff/user-imports/$STARTED")
        waitText(text(R.string.staff090_patrons_import_cancelled))
        compose.waitUntil(10_000) { api.recorded("GET", "staff/user-imports").size >= 2 }
    }

    @Test fun anImportNotStartedYetReopensOnItsMapping() {
        launch(health = health) {
            json("GET staff/user-imports", "s090_patrons_ui_list")
            json("GET staff/user-imports/$READY", "s090_patrons_ui_job_ready")
        }
        openStaffHub()
        click(text(R.string.staff090_user_import))
        clickTag("open-resume-$READY")
        waitRequest("GET", "staff/user-imports/$READY")
        waitFor(androidx.compose.ui.test.hasTestTag("user-import-preview"))
        waitFor(androidx.compose.ui.test.hasTestTag("user-import-start"))
        assertTrue(api.recorded("POST", "staff/user-imports/$READY/chunk").isEmpty())
    }

    private fun FakePinakes.routeJobs() {
        json("POST staff/user-imports/$JOB/start", "s090_patrons_ui_start")
        json("POST staff/user-imports/$JOB/chunk", "s090_patrons_ui_chunk")
        json("GET staff/user-imports/$JOB", "s090_patrons_ui_chunk")
    }

    private companion object {
        const val JOB = "74151523-1959-4ac8-b635-0bd862ae9a27"
        /** The open jobs of s090_patrons_ui_list: readers-a (not started) and readers-b (started). */
        const val READY = "f15dde31-c5f2-4184-8f16-1a6df05246b8"
        const val STARTED = "7eb8631a-3ab1-46ec-b464-ee47f50aa144"
    }
}
