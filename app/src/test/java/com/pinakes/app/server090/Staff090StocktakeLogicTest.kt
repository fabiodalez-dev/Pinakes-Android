package com.pinakes.app.server090

import android.media.AudioManager
import com.pinakes.app.R
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.Staff090Statistics
import com.pinakes.app.data.model.Staff090StocktakeColor
import com.pinakes.app.data.model.Staff090StocktakeLimits
import com.pinakes.app.data.model.Staff090StocktakeOptions
import com.pinakes.app.data.model.Staff090StocktakeScanResult
import com.pinakes.app.data.model.Staff090StocktakeSession
import com.pinakes.app.data.model.Staff090StocktakeState
import com.pinakes.app.data.model.Staff090StocktakeTone
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.network.parseErrorBody
import com.pinakes.app.data.repository.Staff090StatisticsRepository
import com.pinakes.app.data.repository.Staff090StocktakeRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.Staff090StocktakeErrors
import com.pinakes.app.ui.screens.movements.MoneyFormat
import com.pinakes.app.ui.screens.staff.s090.stocktake.Staff090StatisticsBars
import com.pinakes.app.ui.screens.staff.s090.stocktake.Staff090StocktakeClose
import com.pinakes.app.ui.screens.staff.s090.stocktake.Staff090StocktakeColors
import com.pinakes.app.ui.screens.staff.s090.stocktake.Staff090StocktakeCounters
import com.pinakes.app.ui.screens.staff.s090.stocktake.Staff090StocktakeForm
import com.pinakes.app.ui.screens.staff.s090.stocktake.Staff090StocktakeMerge
import com.pinakes.app.ui.screens.staff.s090.stocktake.Staff090StocktakeScanGate
import com.pinakes.app.ui.screens.staff.s090.stocktake.Staff090StocktakeSound
import com.pinakes.app.ui.screens.staff.s090.stocktake.Staff090StocktakeUploadRules
import com.pinakes.app.ui.screens.staff.s090.stocktake.normalizeStocktakeCode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/** The pure decisions of the stocktake and statistics screens, against captured server bodies where they read one. */
class Staff090StocktakeLogicTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }

    private fun raw(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/server090/stocktake/$name.json")).bufferedReader().use { it.readText() }

    private fun <T> data(name: String, serializer: KSerializer<T>): T = json.decodeFromString(Envelope.serializer(serializer), raw(name)).data!!

    private val options by lazy { data("s090_stocktake_options_admin", Staff090StocktakeOptions.serializer()) }

    // ---------- Scan gate: debounce and deliberate replay ----------

    @Test fun aLabelNeedsTwoIdenticalReadsBeforeItIsSent() {
        val gate = Staff090StocktakeScanGate()
        assertNull(gate.offer("A1", 0))
        assertEquals("A1", gate.offer("A1", 40))
    }

    @Test fun aLabelHeldUnderTheCameraIsSentOnceHoweverLongItStays() {
        val gate = Staff090StocktakeScanGate(awayMs = 1_500)
        gate.offer("A1", 0); assertEquals("A1", gate.offer("A1", 40))
        // The camera keeps decoding it for ten seconds: the window slides with every decode.
        var t = 80L
        while (t < 10_000) {
            assertNull("re-sent at $t", gate.offer("A1", t))
            t += 200
        }
    }

    @Test fun readingTheSameLabelAgainAfterMovingAwayIsSent() {
        val gate = Staff090StocktakeScanGate(awayMs = 1_500)
        gate.offer("A1", 0); assertEquals("A1", gate.offer("A1", 40))
        // Out of view for two seconds, then back: a deliberate second read (a repeat for the server).
        assertNull(gate.offer("A1", 2_100))
        assertEquals("A1", gate.offer("A1", 2_140))
    }

    @Test fun aBriefDropoutWhileHoldingTheLabelIsNotASecondRead() {
        val gate = Staff090StocktakeScanGate(awayMs = 1_500)
        gate.offer("A1", 0); gate.offer("A1", 40)
        assertNull(gate.offer("A1", 900))
        assertNull(gate.offer("A1", 2_000)) // 1.1 s after the last decode: still the same hold
    }

    @Test fun anotherLabelInBetweenMakesTheFirstOneNewAgain() {
        val gate = Staff090StocktakeScanGate()
        gate.offer("A1", 0); assertEquals("A1", gate.offer("A1", 40))
        gate.offer("A2", 100); assertEquals("A2", gate.offer("A2", 140))
        gate.offer("A1", 200)
        assertEquals("A1", gate.offer("A1", 240))
    }

    @Test fun aMisreadInBetweenRestartsTheConfirmation() {
        val gate = Staff090StocktakeScanGate()
        assertNull(gate.offer("A1", 0))
        assertNull(gate.offer("A7", 20))
        assertNull(gate.offer("A1", 40))
        assertEquals("A1", gate.offer("A1", 60))
    }

    @Test fun aTypedCodeIsNotSentAgainByTheCameraStraightAway() {
        val gate = Staff090StocktakeScanGate()
        gate.markSent("A1", 0)
        assertNull(gate.offer("A1", 100))
        assertNull(gate.offer("A1", 200))
    }

    @Test fun pausedSendsNothingAndResumeStartsAfresh() {
        val gate = Staff090StocktakeScanGate()
        gate.pause()
        assertNull(gate.offer("A1", 0)); assertNull(gate.offer("A1", 40))
        assertTrue(gate.paused)
        gate.resume()
        assertNull(gate.offer("A1", 80))
        assertEquals("A1", gate.offer("A1", 120))
    }

    @Test fun codesAreNormalisedAsTheServerStoresThem() {
        assertEquals("AST-A1", normalizeStocktakeCode("  AST-A1\n"))
        assertEquals("INV 0042", normalizeStocktakeCode("INV 0042\u0007"))
        assertEquals("", normalizeStocktakeCode(" \t "))
    }

    // ---------- Tone ----------

    @Test fun theScanAnswersToneIsPlayedElseTheOutcomesFromTheOptions() {
        val found = data("s090_stocktake_scan_found", Staff090StocktakeScanResult.serializer())
        assertEquals(Staff090StocktakeTone(880, 120), Staff090StocktakeSound.toneFor(found.scan, found.tone, options))
        // An answer without a tone falls back to the options' tone of its outcome.
        assertEquals(Staff090StocktakeTone(880, 120), Staff090StocktakeSound.toneFor(found.scan, null, options))
        val unknown = data("s090_stocktake_scan_unknown", Staff090StocktakeScanResult.serializer())
        assertEquals(Staff090StocktakeTone(220, 450), Staff090StocktakeSound.toneFor(unknown.scan, unknown.tone, null))
        // Every scan outcome of the options has a tone; missing (never a scan answer) has none.
        options.outcomes.forEach { o -> assertEquals(o.code, o.scanResult, o.tone != null) }
    }

    @Test fun theToneIsSilentUnlessTheRingerIsNormal() {
        val tone = Staff090StocktakeTone(880, 120)
        assertEquals(Staff090StocktakeSound.Spec(880, 120), Staff090StocktakeSound.spec(tone, AudioManager.RINGER_MODE_NORMAL))
        assertNull(Staff090StocktakeSound.spec(tone, AudioManager.RINGER_MODE_SILENT))
        assertNull(Staff090StocktakeSound.spec(tone, AudioManager.RINGER_MODE_VIBRATE))
        assertNull(Staff090StocktakeSound.spec(null, AudioManager.RINGER_MODE_NORMAL))
        assertNull(Staff090StocktakeSound.spec(Staff090StocktakeTone(0, 0), AudioManager.RINGER_MODE_NORMAL))
    }

    @Test fun theToneIsClampedToWhatASpeakerPlays() {
        assertEquals(Staff090StocktakeSound.Spec(150, 40), Staff090StocktakeSound.spec(Staff090StocktakeTone(20, 5), AudioManager.RINGER_MODE_NORMAL))
        assertEquals(Staff090StocktakeSound.Spec(4_000, 800), Staff090StocktakeSound.spec(Staff090StocktakeTone(20_000, 9_000), AudioManager.RINGER_MODE_NORMAL))
    }

    @Test fun theSamplesLastTheToneAndFadeAtBothEnds() {
        val samples = Staff090StocktakeSound.samples(Staff090StocktakeSound.Spec(880, 120), sampleRate = 22_050)
        assertEquals(22_050 * 120 / 1000, samples.size)
        assertEquals(0, samples.first().toInt())
        assertTrue(abs(samples.last().toInt()) < 200)
        assertTrue(samples.maxOf { it.toInt() } > 10_000)
        // 880 Hz: about 106 rising zero crossings in 120 ms.
        val crossings = (1 until samples.size).count { samples[it - 1] < 0 && samples[it] >= 0 }
        assertTrue("crossings $crossings", crossings in 100..110)
    }

    // ---------- Colours ----------

    @Test fun outcomeColoursComeFromTheServerHex() {
        assertEquals(0xFF166534L, Staff090StocktakeColors.argb("#166534"))
        assertEquals(0xFFDCFCE7L, Staff090StocktakeColors.argb("dcfce7"))
        assertEquals(0xFFAABBCCL, Staff090StocktakeColors.argb("#abc"))
        assertNull(Staff090StocktakeColors.argb("green"))
        assertNull(Staff090StocktakeColors.argb(null))
        val misplaced = data("s090_stocktake_scan_misplaced", Staff090StocktakeScanResult.serializer()).scan
        assertEquals("amber", Staff090StocktakeColors.forOutcome(misplaced.outcome, misplaced.color, null)?.name)
        // A row without a usable colour takes the options' one, else none (the neutral pair).
        assertEquals("red", Staff090StocktakeColors.forOutcome("unknown", Staff090StocktakeColor("x", "", ""), options)?.name)
        assertNull(Staff090StocktakeColors.forOutcome("whatever", null, options))
    }

    // ---------- Counters, merge, polling ----------

    @Test fun countersListEveryWebCounterAndLostOnlyOnceMarked() {
        val open = data("s090_stocktake_state", Staff090StocktakeState.serializer())
        val keys = Staff090StocktakeCounters.of(open.summary).map { it.key }
        assertEquals(listOf("expected", "found", "misplaced", "on_loan", "missing", "unknown", "out_of_scope", "duplicates", "mine"), keys)
        val lost = data("s090_stocktake_closed_lost", Staff090StocktakeSession.serializer())
        assertTrue("lost" in Staff090StocktakeCounters.of(lost.summary).map { it.key })
        assertEquals(2, Staff090StocktakeCounters.of(lost.summary).single { it.key == "lost" }.value)
    }

    @Test fun aScanAnswerGoesOnTopOnceAndAClosedSessionStopsScanning() {
        val detail = data("s090_stocktake_session_detail", Staff090StocktakeSession.serializer())
        val newest = detail.recentScans.first()
        assertEquals(detail.recentScans.size, Staff090StocktakeMerge.prepend(detail.recentScans, newest).size)
        val fresh = newest.copy(id = newest.id + 1000)
        assertEquals(fresh, Staff090StocktakeMerge.prepend(detail.recentScans, fresh).first())
        assertEquals(3, Staff090StocktakeMerge.prepend(detail.recentScans, fresh, max = 3).size)
        assertTrue(Staff090StocktakeMerge.shouldPoll(detail))
        val closed = Staff090StocktakeMerge.withStatus(detail, "closed")
        assertFalse(closed.canScan || closed.canUpload || closed.canClose || closed.canCancel)
        assertFalse(Staff090StocktakeMerge.shouldPoll(closed))
        assertEquals(detail, Staff090StocktakeMerge.withStatus(detail, "open"))
        assertFalse(Staff090StocktakeMerge.shouldPoll(null))
    }

    // ---------- Create form ----------

    @Test fun theCreateFormSendsOnlyWhatWasChosen() {
        val limits = Staff090StocktakeLimits()
        val blank = Staff090StocktakeForm()
        assertFalse(blank.valid(limits))
        assertFalse(blank.copy(name = "   ").valid(limits))
        val form = Staff090StocktakeForm(name = "  Shelf A  ", note = " ", collocationFrom = " A ", mediaType = "")
        assertTrue(form.valid(limits))
        val body = json.encodeToJsonElement(com.pinakes.app.data.model.Staff090StocktakeCreateRequest.serializer(), form.request()).jsonObject
        assertEquals(setOf("name", "collocation_from"), body.keys)
        assertFalse(form.copy(collocationTo = "x".repeat(51)).valid(limits))
        assertFalse(form.copy(name = "x".repeat(256)).valid(limits))
    }

    @Test fun aLevelBelongsToItsShelf() {
        val shelf = options.shelves.first { it.levels.isNotEmpty() }
        val other = options.shelves.first { it.id != shelf.id }
        val form = Staff090StocktakeForm(name = "x").withShelf(shelf.id).copy(levelId = shelf.levels.first().id)
        assertEquals(shelf.levels, form.levels(options.shelves))
        assertEquals(shelf.levels.first().id, form.withShelf(shelf.id).levelId)
        assertNull(form.withShelf(other.id).levelId)
        assertNull(form.withShelf(null).request().levelId)
        assertEquals(shelf.id, form.request().shelfId)
    }

    // ---------- Close ----------

    @Test fun lostIsOfferedOnlyWhenTheServerSaysSo() {
        val staff = data("s090_stocktake_session_detail", Staff090StocktakeSession.serializer())
        assertEquals(listOf("none"), Staff090StocktakeClose.actions(staff))
        val admin = data("s090_stocktake_session_detail_mine", Staff090StocktakeSession.serializer())
        assertEquals(listOf("none", "lost"), Staff090StocktakeClose.actions(admin))
        assertEquals(staff.summary.missing, Staff090StocktakeClose.affected(staff))
        assertEquals(R.string.staff090_stocktake_close_confirm_none, Staff090StocktakeClose.confirmText(staff, "none"))
        assertEquals(R.string.staff090_stocktake_close_confirm_lost, Staff090StocktakeClose.confirmText(admin, "lost"))
        val closed = data("s090_stocktake_closed_none", Staff090StocktakeSession.serializer())
        assertTrue(Staff090StocktakeClose.actions(closed).isEmpty())
        assertFalse(Staff090StocktakeClose.canResume(closed))
        // A mark-lost close cut short: closed, still markable by the server's word, with copies pending.
        val cut = data("s090_stocktake_closed_lost", Staff090StocktakeSession.serializer()).copy(canMarkLost = true, pendingLost = 3)
        assertTrue(Staff090StocktakeClose.canResume(cut))
        assertEquals(listOf("lost"), Staff090StocktakeClose.actions(cut))
        assertEquals(3, Staff090StocktakeClose.affected(cut))
        assertEquals(R.string.staff090_stocktake_close_confirm_resume, Staff090StocktakeClose.confirmText(cut, "lost"))
    }

    // ---------- Upload ----------

    @Test fun onlyTxtAndCsvUpToTheLimitAreUploaded() {
        val limits = Staff090StocktakeUploadRules.Limits.of(options.limits)
        assertTrue(Staff090StocktakeUploadRules.acceptsName("codes.TXT", limits))
        assertTrue(Staff090StocktakeUploadRules.acceptsName("shelf a.csv", limits))
        assertFalse(Staff090StocktakeUploadRules.acceptsName("codes.pdf", limits))
        assertFalse(Staff090StocktakeUploadRules.acceptsName("codes", limits))
        assertTrue(Staff090StocktakeUploadRules.acceptsSize(2_097_152, limits))
        assertFalse(Staff090StocktakeUploadRules.acceptsSize(2_097_153, limits))
        assertFalse(Staff090StocktakeUploadRules.acceptsSize(0, limits))
    }

    // ---------- Errors ----------

    @Test fun errorCodesOfTheArea() {
        val refused = parseErrorBody(raw("s090_stocktake_close_lost_refused"), 403)
        assertTrue(Staff090StocktakeErrors.isNotPermitted(refused))
        // not_permitted is not the role gate: the stored role stays.
        assertFalse(refused.isForbidden())
        assertNull(Staff090StocktakeErrors.messageRes(refused.code))
        val closed = parseErrorBody(raw("s090_stocktake_scan_session_closed"), 409)
        assertTrue(Staff090StocktakeErrors.sessionEnded(closed))
        assertEquals(R.string.staff090_stocktake_err_session_closed, Staff090StocktakeErrors.messageRes(closed.code))
        assertTrue(Staff090StocktakeErrors.sessionEnded(parseErrorBody(raw("s090_stocktake_scan_session_cancelled"), 409)))
        assertEquals(R.string.staff090_stocktake_err_report_too_large, Staff090StocktakeErrors.messageRes("report_too_large"))
        assertFalse(Staff090StocktakeErrors.sessionEnded(ApiResult.Failure("session_closed", "proxy", 409, fromEnvelope = false)))
        assertFalse(Staff090StocktakeErrors.isNotPermitted(ApiResult.Failure(ErrorCodes.FORBIDDEN, "", 403, fromEnvelope = true)))
        assertTrue(Staff090StocktakeErrors.moduleOff(ApiResult.Failure("feature_disabled", "", 404, fromEnvelope = true)))
    }

    // ---------- Statistics ----------

    @Test fun barsScaleToTheLargestAndMonthsReadAsMonths() {
        assertEquals(1f, Staff090StatisticsBars.fraction(15, 15))
        assertEquals(0.5f, Staff090StatisticsBars.fraction(5, 10))
        assertEquals(0.02f, Staff090StatisticsBars.fraction(1, 1000))
        assertEquals(0f, Staff090StatisticsBars.fraction(0, 10))
        assertEquals(0f, Staff090StatisticsBars.fraction(3, 0))
        assertEquals("Sep 2026", Staff090StatisticsBars.monthLabel("2026-09", Locale.ENGLISH))
        assertEquals("not-a-month", Staff090StatisticsBars.monthLabel("not-a-month", Locale.ENGLISH))
    }

    @Test fun finesAmountsAreFormattedInTheServerCurrency() {
        val report = data("s090_statistics_full", Staff090Statistics.serializer()).finesReport!!
        val it = MoneyFormat.format(report.totals.chargedCents, report.currency, Locale.ITALY)
        assertTrue(it, it.contains("12,50") && it.contains("€"))
        assertEquals("€5.00", MoneyFormat.format(report.totals.collectedCents, report.currency, Locale.UK))
    }

    @Test fun downloadPaths() {
        assertEquals("staff/statistics/fines.csv?fines_from=2026-01-01&fines_to=2026-11-30", Staff090StatisticsRepository.finesCsvPath("2026-01-01", "2026-11-30"))
        assertEquals("staff/statistics/fines.csv", Staff090StatisticsRepository.finesCsvPath(null, " "))
        assertEquals("staff/stocktake/sessions/41/report.csv", Staff090StocktakeRepository.reportCsvPath(41))
        assertEquals("staff/stocktake/sessions/41/report.pdf", Staff090StocktakeRepository.reportPdfPath(41))
    }
}
