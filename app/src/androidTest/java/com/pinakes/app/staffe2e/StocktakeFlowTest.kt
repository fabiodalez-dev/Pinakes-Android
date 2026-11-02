package com.pinakes.app.staffe2e

import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import com.pinakes.app.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Hub → Stocktake and Statistics, against bodies captured from a Pinakes 0.9.0 server (Mobile
 * API 1.10.0): open a session, record a typed code with the current shelf and an
 * Idempotency-Key, see the answer and the polled counters, create a session, close it, a
 * refused mark-lost close that keeps the staff role, and the statistics' fines CSV.
 */
class StocktakeFlowTest : AppFlowTest() {

    private val health = "s090_stocktake_health"
    private val json = Json { ignoreUnknownKeys = true }

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).close()

    /**
     * The scanner screens are driven by typed codes: the camera stays refused without a system
     * prompt (user-fixed), so no permission dialog can cover the screen halfway through a test.
     */
    @Before fun refuseCamera() {
        shell("pm revoke ${context.packageName} android.permission.CAMERA")
        shell("pm set-permission-flags ${context.packageName} android.permission.CAMERA user-fixed")
    }

    @After fun restoreCamera() {
        shell("pm clear-permission-flags ${context.packageName} android.permission.CAMERA user-fixed")
    }

    private fun fixtureData(name: String) = json.parseToJsonElement(FakePinakes.fixture(name)).jsonObject["data"]!!.jsonObject

    /** The open session of the captures, its shelf and the text of a found answer. */
    private val sessionId by lazy { fixtureData("s090_stocktake_session_detail")["id"]!!.jsonPrimitive.int }
    private val shelfId by lazy { fixtureData("s090_stocktake_session_detail")["filter"]!!.jsonObject["shelf_id"]!!.jsonPrimitive.int }
    private val sessionName by lazy { fixtureData("s090_stocktake_session_detail")["name"]!!.jsonPrimitive.content }

    private fun FakePinakes.stocktake(detail: String = "s090_stocktake_session_detail", options: String = "s090_stocktake_options_staff") {
        json("GET me", "me_staff")
        json("GET staff/stocktake/options", options)
        json("GET staff/stocktake/sessions", "s090_stocktake_sessions_open")
        json("GET staff/stocktake/sessions/$sessionId", detail)
        json("GET staff/stocktake/sessions/$sessionId/state", "s090_stocktake_state")
    }

    private fun openSession() {
        openStaffHub()
        click(text(R.string.staff090_stocktake))
        clickTag("session-$sessionId")
        pastCameraPrompt()
    }

    /** The scanner asks for the camera when it first shows: deny it (manual entry stays) until the session screen is back in front. */
    private fun pastCameraPrompt() {
        val input = androidx.compose.ui.test.hasTestTag("stocktake-code-input")
        repeat(4) {
            denyCameraPromptIfShown()
            if (runCatching { waitFor(input, 4_000) }.isSuccess) return
        }
        waitFor(input, 1)
    }

    /** Scroll the session list to [tag] (items below the scanner are not composed until then), then click it. */
    private fun scrollAndClick(tag: String) {
        val list = compose.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())
        compose.waitUntil(10_000) { list.fetchSemanticsNodes().isNotEmpty() }
        list.onFirst().performScrollToNode(androidx.compose.ui.test.hasTestTag(tag))
        clickTag(tag)
    }

    @Test fun aTypedCodeIsRecordedWithTheShelfAndAKeyAndTheCountersFollowThePoll() {
        launch(role = "staff", health = health) {
            stocktake()
            json("POST staff/stocktake/sessions/$sessionId/scans", "s090_stocktake_scan_found", status = 201)
        }
        openSession()
        typeIntoTag("stocktake-code-input", "AST-A1")
        clickDescription(text(R.string.staff090_stocktake_send_code))
        assertEquals("""{"code":"AST-A1","shelf_id":$shelfId}""", lastBody("POST", "staff/stocktake/sessions/$sessionId/scans"))
        assertEquals(36, api.recorded("POST", "staff/stocktake/sessions/$sessionId/scans").last().getHeader("Idempotency-Key").orEmpty().length)
        // The answer, in its outcome's words, at once.
        val label = fixtureData("s090_stocktake_scan_found")["scan"]!!.jsonObject["outcome_label"]!!.jsonPrimitive.content
        waitText(label)
        // Polling while visible, so other operators' scans show up.
        waitRequest("GET", "staff/stocktake/sessions/$sessionId/state", timeoutMs = 12_000)
        assertEquals("staff", deps.session().role.value)
    }

    @Test fun aNewSessionIsOpenedOnceWithItsIdempotencyKey() {
        launch(role = "staff", health = health) {
            stocktake()
            json("POST staff/stocktake/sessions", "s090_stocktake_session_created", status = 201)
        }
        openStaffHub()
        click(text(R.string.staff090_stocktake))
        clickDescription(text(R.string.staff090_stocktake_new))
        typeIntoTag("stocktake-name", "Shelf X from the phone")
        clickTag("stocktake-create")
        pastCameraPrompt()
        assertEquals("""{"name":"Shelf X from the phone"}""", lastBody("POST", "staff/stocktake/sessions"))
        assertEquals(36, api.recorded("POST", "staff/stocktake/sessions").last().getHeader("Idempotency-Key").orEmpty().length)
        // The new session opens on its scanner.
        waitRequest("GET", "staff/stocktake/sessions/$sessionId")
        waitText(sessionName)
    }

    @Test fun closingStatesTheMissingCountAndLeavesThemAlone() {
        launch(role = "staff", health = health) {
            stocktake()
            // A slow poll: the one in flight when the close lands still says "open".
            json("GET staff/stocktake/sessions/$sessionId/state", "s090_stocktake_state", delayMs = 8_000)
            json("POST staff/stocktake/sessions/$sessionId/close", "s090_stocktake_closed_none")
        }
        openSession()
        // Close while a poll is in flight: its stale "open" must not bring the session back.
        waitRequest("GET", "staff/stocktake/sessions/$sessionId/state")
        scrollAndClick("stocktake-close")
        val missing = fixtureData("s090_stocktake_session_detail")["summary"]!!.jsonObject["missing"]!!.jsonPrimitive.int
        waitText(text(R.string.staff090_stocktake_close_confirm_none, missing))
        // Staff is not offered "mark as lost".
        assertFalse(exists(text(R.string.staff090_stocktake_action_lost)))
        clickTag("close-confirm")
        assertEquals("""{"missing_action":"none"}""", lastBody("POST", "staff/stocktake/sessions/$sessionId/close"))
        // The server's sentence about the close, and no close action any more.
        waitText(fixtureData("s090_stocktake_closed_none")["close_result"]!!.jsonObject["message"]!!.jsonPrimitive.content)
        waitGone(androidx.compose.ui.test.hasTestTag("stocktake-close"))
        // Let the slow poll answer (8 s after it left), then the session must still be closed.
        Thread.sleep(9_000)
        compose.waitForIdle()
        assertFalse(compose.onAllNodes(androidx.compose.ui.test.hasTestTag("stocktake-close")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(exists(fixtureData("s090_stocktake_closed_none")["close_result"]!!.jsonObject["message"]!!.jsonPrimitive.content))
    }

    @Test fun aRefusedMarkLostShowsTheServerMessageAndKeepsTheRole() {
        // The session still offers "lost" (an administrator's view), the server refuses it to this account.
        launch(role = "staff", health = health) {
            stocktake(detail = "s090_stocktake_session_detail_mine")
            json("POST staff/stocktake/sessions/$sessionId/close", "s090_stocktake_close_lost_refused", status = 403)
        }
        openSession()
        scrollAndClick("stocktake-close")
        clickTag("close-lost")
        clickTag("close-confirm")
        assertEquals("""{"missing_action":"lost"}""", lastBody("POST", "staff/stocktake/sessions/$sessionId/close"))
        val message = json.parseToJsonElement(FakePinakes.fixture("s090_stocktake_close_lost_refused")).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
        waitText(message)
        assertEquals("staff", deps.session().role.value)
        assertTrue(exists(sessionName))
    }

    @Test fun theReportListsTheRowsAndDownloadsTheCsv() {
        launch(role = "staff", health = health) {
            stocktake()
            json("GET staff/stocktake/sessions/$sessionId/report", "s090_stocktake_report")
            body("GET staff/stocktake/sessions/$sessionId/report.csv", 200, "﻿Esito,Codice\nTrovata,AST-A1\n",
                mapOf("Content-Type" to "text/csv; charset=utf-8", "Content-Disposition" to "attachment; filename=\"revisione-$sessionId.csv\""))
        }
        openSession()
        clickDescription(text(R.string.staff090_stocktake_report))
        val code = json.parseToJsonElement(FakePinakes.fixture("s090_stocktake_report")).jsonObject["data"]!!.let { (it as kotlinx.serialization.json.JsonArray).first().jsonObject["code"]!!.jsonPrimitive.content }
        waitText(code)
        clickTag("report-csv")
        waitRequest("GET", "staff/stocktake/sessions/$sessionId/report.csv")
        waitText("revisione-$sessionId.csv")
    }

    @Test fun statisticsShowTheFinesReportAndDownloadItsCsv() {
        launch(role = "staff", health = health) {
            json("GET me", "me_staff")
            json("GET staff/statistics", "s090_statistics_full")
            body("GET staff/statistics/fines.csv", 200, "﻿Mese,Addebitato\n2026-11,12.50\n",
                mapOf("Content-Type" to "text/csv; charset=UTF-8", "Content-Disposition" to "attachment; filename=\"multe_2026-01-01_2026-11-30.csv\""))
        }
        openStaffHub()
        click(text(R.string.staff090_statistics))
        waitText(text(R.string.staff090_statistics_overview).uppercase())
        scrollListTo(text(R.string.staff090_statistics_fines_csv))
        clickTag("fines-csv")
        waitRequest("GET", "staff/statistics/fines.csv")
        val url = api.recorded("GET", "staff/statistics/fines.csv").last().requestUrl!!
        assertEquals("2026-01-01", url.queryParameter("fines_from"))
        assertEquals("2026-11-30", url.queryParameter("fines_to"))
        waitText("multe_2026-01-01_2026-11-30.csv")
    }
}
