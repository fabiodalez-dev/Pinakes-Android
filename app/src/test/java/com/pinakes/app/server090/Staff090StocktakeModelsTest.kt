package com.pinakes.app.server090

import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.HealthPayload
import com.pinakes.app.data.model.Staff090Statistics
import com.pinakes.app.data.model.Staff090StocktakeOptions
import com.pinakes.app.data.model.Staff090StocktakeReportRow
import com.pinakes.app.data.model.Staff090StocktakeScan
import com.pinakes.app.data.model.Staff090StocktakeScanResult
import com.pinakes.app.data.model.Staff090StocktakeSession
import com.pinakes.app.data.model.Staff090StocktakeState
import com.pinakes.app.data.model.Staff090StocktakeUpload
import com.pinakes.app.data.network.parseErrorBody
import com.pinakes.app.data.staff.Staff090Areas
import com.pinakes.app.data.store.InstanceFeatures
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stocktake and statistics areas (Mobile API 1.10.0) decode the bodies the server really
 * sends: every sample under `test/resources/server090/stocktake` was captured with curl from a
 * Pinakes 0.9.0 instance (feat/mobile-staff-090) as an administrator and as a staff member, on
 * shelves and copies created for the capture and removed afterwards.
 */
class Staff090StocktakeModelsTest {

    // Same configuration as NetworkModule.json.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }

    private fun raw(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/server090/stocktake/$name.json")) { "missing sample $name" }.bufferedReader().use { it.readText() }

    private fun <T> envelope(name: String, serializer: KSerializer<T>): Envelope<T> = json.decodeFromString(Envelope.serializer(serializer), raw(name))

    private fun <T> data(name: String, serializer: KSerializer<T>): T = requireNotNull(envelope(name, serializer).data) { "no data in $name" }

    @Test fun healthAdvertisesBothAreasAndTheyHideOffline() {
        val health = data("s090_stocktake_health", HealthPayload.serializer())
        assertTrue(health.features.staffStocktake && health.features.staffStatistics && health.features.stocktake)
        val areas = Staff090Areas.of("staff", InstanceFeatures.from(health), online = true)
        assertTrue(areas.stocktake && areas.statistics)
        val offline = Staff090Areas.of("staff", InstanceFeatures.from(health), online = false)
        assertFalse(offline.stocktake || offline.statistics)
    }

    @Test fun optionsCarryShelvesOutcomesWithColourAndToneAndTheAdminOnlyAction() {
        val admin = data("s090_stocktake_options_admin", Staff090StocktakeOptions.serializer())
        assertTrue(admin.canMarkLost)
        val shelf = admin.shelves.first()
        assertTrue(shelf.label.contains(shelf.code))
        assertEquals(1, shelf.levels.single().level)
        val found = admin.outcomes.single { it.code == "found" }
        assertEquals("green", found.color?.name)
        assertEquals("#166534", found.color?.foreground)
        assertEquals(880, found.tone?.frequencyHz)
        assertEquals(120, found.tone?.durationMs)
        val missing = admin.outcomes.single { it.code == "missing" }
        assertFalse(missing.scanResult)
        assertNull(missing.tone)
        assertEquals(listOf("duplicate", "found", "misplaced", "missing", "on_loan", "out_of_scope", "unknown"), admin.outcomes.map { it.code }.sorted())
        assertEquals(listOf("libro", "disco", "audiolibro", "dvd", "altro"), admin.mediaTypes.map { it.code })
        assertTrue(admin.missingActions.single { it.code == "lost" }.adminOnly)
        assertEquals(100, admin.limits.maxCodeLength)
        assertEquals(2_097_152L, admin.limits.maxUploadBytes)
        assertEquals(listOf("txt", "csv"), admin.limits.uploadExtensions)
        assertEquals(listOf("open", "closed", "cancelled"), admin.statuses.map { it.code })
        assertTrue(admin.genres.isNotEmpty())
        val staff = data("s090_stocktake_options_staff", Staff090StocktakeOptions.serializer())
        assertFalse(staff.canMarkLost)
    }

    @Test fun aCreatedSessionAndItsIdempotentReplay() {
        val created = data("s090_stocktake_session_created", Staff090StocktakeSession.serializer())
        assertTrue(created.isOpen)
        assertEquals(4, created.expectedTotal)
        assertEquals(created.summary.expected, created.expectedTotal)
        assertEquals("From the phone", created.note)
        assertNotNull(created.filter.shelfId)
        assertNull(created.filter.levelId)
        assertTrue(created.filterLabels.isNotEmpty())
        assertTrue(created.canScan && created.canUpload && created.canClose && created.canCancel)
        assertFalse(created.canMarkLost)
        assertEquals("inventory.opened", created.history.single().event)
        assertTrue(created.openedAt!!.endsWith("+00:00"))
        val replay = envelope("s090_stocktake_session_replayed", Staff090StocktakeSession.serializer())
        assertEquals(true, replay.meta?.replayed)
        assertEquals(created.id, replay.data?.id)
    }

    @Test fun everyScanOutcomeComesWithItsColourToneAndCounters() {
        val found = data("s090_stocktake_scan_found", Staff090StocktakeScanResult.serializer())
        assertEquals("found", found.scan.outcome)
        assertEquals(880, found.tone?.frequencyHz)
        assertEquals(1, found.summary.found)
        assertEquals(1, found.summary.mine)
        assertEquals("open", found.sessionStatus)
        assertNotNull(found.scan.copyId)
        assertNotNull(found.scan.operator)
        val misplaced = data("s090_stocktake_scan_misplaced", Staff090StocktakeScanResult.serializer())
        assertEquals("misplaced", misplaced.scan.outcome)
        assertTrue(misplaced.scan.expectedShelf!!.isNotBlank())
        assertEquals("amber", misplaced.scan.color?.name)
        val onLoan = data("s090_stocktake_scan_on_loan", Staff090StocktakeScanResult.serializer())
        assertEquals("on_loan", onLoan.scan.outcome)
        val unknown = data("s090_stocktake_scan_unknown", Staff090StocktakeScanResult.serializer())
        assertEquals("unknown", unknown.scan.outcome)
        assertNull(unknown.scan.copyId)
        assertNull(unknown.scan.title)
        assertEquals(220, unknown.tone?.frequencyHz)
        val outOfScope = data("s090_stocktake_scan_out_of_scope", Staff090StocktakeScanResult.serializer())
        assertEquals("out_of_scope", outOfScope.scan.outcome)
        assertFalse(outOfScope.scan.offShelf)
        // A repeat is stored without its copy, but the answer still names it.
        val duplicate = data("s090_stocktake_scan_duplicate", Staff090StocktakeScanResult.serializer())
        assertEquals("duplicate", duplicate.scan.outcome)
        assertNotNull(duplicate.scan.copyId)
        assertEquals(1, duplicate.summary.duplicates)
        assertEquals(6, duplicate.summary.scans)
    }

    @Test fun detailStateAndThePagedScanList() {
        val detail = data("s090_stocktake_session_detail", Staff090StocktakeSession.serializer())
        assertEquals(6, detail.recentScans.size)
        assertEquals("duplicate", detail.recentScans.first().outcome)
        val mine = data("s090_stocktake_session_detail_mine", Staff090StocktakeSession.serializer())
        assertEquals(1, mine.recentScans.size)
        assertTrue(mine.canMarkLost)
        val state = data("s090_stocktake_state", Staff090StocktakeState.serializer())
        assertEquals(detail.id, state.id)
        assertEquals("open", state.status)
        assertEquals(detail.summary, state.summary)
        val page = envelope("s090_stocktake_scans_page", ListSerializer(Staff090StocktakeScan.serializer()))
        assertEquals(4, page.data!!.size)
        assertEquals(true, page.meta?.hasMore)
        assertNotNull(page.meta?.nextCursor)
        assertEquals(6, page.meta?.totalCount)
        val foundMine = data("s090_stocktake_scans_found_mine", ListSerializer(Staff090StocktakeScan.serializer()))
        assertTrue(foundMine.all { it.outcome == "found" })
        val sessions = envelope("s090_stocktake_sessions_open", ListSerializer(Staff090StocktakeSession.serializer()))
        assertTrue(sessions.data!!.all { it.isOpen })
        assertTrue(sessions.data!!.all { it.recentScans.isEmpty() })
        assertEquals(false, sessions.meta?.hasMore)
        val all = data("s090_stocktake_sessions_all", ListSerializer(Staff090StocktakeSession.serializer()))
        assertTrue(setOf("open", "closed", "cancelled").containsAll(all.map { it.status }))
    }

    @Test fun reportRowsAndTheUploadAnswer() {
        val report = envelope("s090_stocktake_report", ListSerializer(Staff090StocktakeReportRow.serializer()))
        val rows = report.data!!
        assertEquals(report.meta?.totalCount, rows.size)
        val missing = rows.first { it.category == "missing" }
        assertNull(missing.scannedAt)
        assertTrue(missing.expectedShelf!!.isNotBlank())
        val onlyMissing = data("s090_stocktake_report_missing", ListSerializer(Staff090StocktakeReportRow.serializer()))
        assertTrue(onlyMissing.all { it.category == "missing" })
        val upload = data("s090_stocktake_upload", Staff090StocktakeUpload.serializer())
        assertEquals(2, upload.total)
        assertEquals(1, upload.outcomes.single { it.code == "found" }.count)
        assertEquals(1, upload.outcomes.single { it.code == "unknown" }.count)
        assertTrue(upload.message.isNotBlank())
    }

    @Test fun closeCancelAndTheirRefusals() {
        val none = data("s090_stocktake_closed_none", Staff090StocktakeSession.serializer())
        assertEquals("closed", none.status)
        assertEquals("none", none.missingAction)
        assertFalse(none.canScan || none.canClose || none.canCancel)
        assertEquals(0, none.closeResult!!.markedLost)
        assertTrue(none.closeResult!!.message.isNotBlank())
        assertNotNull(none.closedBy)
        val lost = data("s090_stocktake_closed_lost", Staff090StocktakeSession.serializer())
        assertEquals("lost", lost.missingAction)
        assertEquals(2, lost.closeResult!!.markedLost)
        assertEquals(2, lost.summary.lost)
        assertFalse(lost.closeResult!!.partial)
        val cancelled = data("s090_stocktake_cancelled", Staff090StocktakeSession.serializer())
        assertEquals("cancelled", cancelled.status)
        assertTrue(cancelled.message!!.isNotBlank())

        val refused = parseErrorBody(raw("s090_stocktake_close_lost_refused"), 403)
        assertEquals("not_permitted", refused.code)
        assertTrue(refused.fromEnvelope)
        assertTrue(refused.message.contains("amministratore"))
        val closed = parseErrorBody(raw("s090_stocktake_scan_session_closed"), 409)
        assertEquals("session_closed", closed.code)
        assertEquals("session_cancelled", parseErrorBody(raw("s090_stocktake_scan_session_cancelled"), 409).code)
        val invalid = parseErrorBody(raw("s090_stocktake_create_invalid"), 422)
        assertEquals("validation", invalid.code)
        assertEquals("Indica un nome per la revisione.", invalid.message)
        assertEquals("validation", parseErrorBody(raw("s090_stocktake_scan_invalid"), 422).code)
        assertEquals("validation", parseErrorBody(raw("s090_stocktake_upload_invalid"), 422).code)
    }

    @Test fun statisticsWithEveryModuleOn() {
        val s = data("s090_statistics_full", Staff090Statistics.serializer())
        assertTrue(s.overview.completedLoans > 0)
        assertTrue(s.loansByMonth.all { Regex("""\d{4}-\d{2}""").matches(it.month) })
        assertEquals(listOf("libro", "disco", "audiolibro", "dvd", "altro"), s.loansByMediaType.map { it.mediaType })
        assertTrue(s.topBooks.isNotEmpty() && s.topBooks.all { it.coverUrl!!.startsWith("http") })
        assertTrue(s.topReaders.isNotEmpty())
        val visitors = requireNotNull(s.visitors)
        assertEquals(30, visitors.days)
        assertEquals(30, visitors.daily.size)
        val fines = s.finesReport!!
        assertEquals("EUR", fines.currency)
        assertEquals("2026-01-01", fines.from)
        assertEquals(1250, fines.totals.chargedCents)
        assertEquals(500, fines.totals.collectedCents)
        assertEquals(1250, fines.months.single().chargedCents)
        // Section rows mix numbers and texts already formatted with the currency.
        val finesSection = s.sections.single { it.id == "fines" }
        assertEquals("12,50 €", finesSection.rows.first().display)
        assertEquals("1", finesSection.rows.last().display)
        assertTrue(s.sections.single { it.id == "patron-types" }.note!!.isNotBlank())
    }

    @Test fun statisticsLeaveOutTheSectionsOfModulesOff() {
        val s = data("s090_statistics_modules_off", Staff090Statistics.serializer())
        assertNull(s.visitors)
        assertNull(s.finesReport)
        assertFalse(s.sections.any { it.id == "fines" || it.id == "stocktake" })
        val staff = data("s090_statistics_staff", Staff090Statistics.serializer())
        val stocktake = staff.sections.single { it.id == "stocktake" }
        assertTrue(stocktake.rows.isNotEmpty())
        val bad = parseErrorBody(raw("s090_statistics_bad_date"), 422)
        assertEquals("validation", bad.code)
        assertEquals("fines_from", bad.meta?.field)
    }
}
