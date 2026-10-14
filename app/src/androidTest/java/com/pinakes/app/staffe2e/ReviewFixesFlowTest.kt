package com.pinakes.app.staffe2e

import com.pinakes.app.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Regressions from the phase 2 review: batching, paging, failed loads, ended jobs, exports on sign-out. */
class ReviewFixesFlowTest : AppFlowTest() {

    @Test fun bulkRecallOfSixtyLoansGoesInTwoBatches() {
        launch {
            json("GET staff/circulation/summary", "circulation_summary")
            json("GET staff/loans", "staff_loans_overdue_60")
            sequence("POST staff/loans/bulk-recall", 200 to "bulk_recall_50", 200 to "bulk_recall_10")
        }
        openStaffHub()
        click(text(R.string.staff_circulation_desk))
        clickTag("counter-overdue")
        waitText("Overdue 00")
        clickDescription(text(R.string.staff_select_many))
        click(text(R.string.staff_select_all))
        waitText(text(R.string.staff_selected_count, 60))
        click(text(R.string.staff_loan_action_recall))
        compose.waitUntil(10_000) { api.recorded("POST", "staff/loans/bulk-recall").size == 2 }
        val sizes = api.recorded("POST", "staff/loans/bulk-recall").map { r ->
            Regex("""\[(.*)]""").find(r.body.clone().readUtf8())!!.groupValues[1].split(',').size
        }
        assertEquals(listOf(50, 10), sizes)
        waitText(text(R.string.staff_bulk_recalled, 59, 1))
    }

    @Test fun reservationsLoadTheNextPage() {
        launch {
            sequence("GET staff/reservations", 200 to "staff_reservations_page1", 200 to "staff_reservations_page2")
        }
        openStaffHub()
        click(text(R.string.staff_reservations))
        waitText("Page two title", timeoutMs = 15_000)
        assertEquals("Mg", api.recorded("GET", "staff/reservations").last().requestUrl?.queryParameter("cursor"))
    }

    @Test fun anAuthorityThatFailedToLoadCannotBeSaved() {
        launch {
            json("GET staff/archives/lookups", "archive_lookups")
            json("GET staff/archives/authorities", "authorities_list")
            body("GET staff/archives/authorities/7", 500, """{"data":null,"meta":{},"error":{"code":"internal_error","message":"Authority failed"}}""")
        }
        openStaffHub()
        click(text(R.string.staff_authorities))
        click("Rossi, Mario")
        waitText("Authority failed")
        assertFalse(compose.onAllNodes(androidx.compose.ui.test.hasTestTag("authority-save")).fetchSemanticsNodes().isNotEmpty())
        api.json("GET staff/archives/authorities/7", "authority_7")
        click(text(R.string.action_retry))
        waitText("Notaio.")
        waitFor(androidx.compose.ui.test.hasTestTag("authority-save"))
        assertTrue(api.recorded("PUT", "staff/archives/authorities/7").isEmpty())
    }

    @Test fun aFailedDuplicateListIsReportedWithRetry() {
        launch {
            body("GET staff/authors/duplicates", 500, """{"data":null,"meta":{},"error":{"code":"internal_error","message":"Duplicates failed"}}""")
        }
        openStaffHub()
        click(text(R.string.staff_merges))
        waitText("Duplicates failed")
        assertFalse(exists(text(R.string.staff_merge_no_duplicates)))
        api.json("GET staff/authors/duplicates", "staff_author_duplicates")
        click(text(R.string.action_retry))
        waitText(text(R.string.staff_merge_author_books, "Woolf, Virginia", 1))
    }

    @Test fun anImportThatEndedElsewhereIsNotResumable() {
        launch {
            json("GET staff/imports", "imports_overview")
            json("GET staff/imports/6f1c2a", "import_job_created")
            body("POST staff/imports/6f1c2a/chunk", 410, """{"data":null,"meta":{},"error":{"code":"gone","message":"Import expired"}}""")
        }
        openStaffHub()
        click(text(R.string.staff_imports))
        scrollListTo("libri.csv")
        click("libri.csv")
        waitText(text(R.string.staff_import_status_gone))
        assertFalse(exists(text(R.string.staff_import_resume)))
        assertFalse(exists(text(R.string.staff_import_cancel)))
    }

    @Test fun staffExportsAreDeletedOnSignOut() {
        launch()
        val exports = File(context.cacheDir, "exports").apply { mkdirs() }
        File(exports, "prestito_7_20261014.pdf").writeText("%PDF-1.4")
        runBlocking { deps.authRepository().logout() }
        assertFalse(File(exports, "prestito_7_20261014.pdf").exists())
    }
}
