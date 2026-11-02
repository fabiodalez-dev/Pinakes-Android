package com.pinakes.app.server090

import com.pinakes.app.R
import com.pinakes.app.data.model.AttendanceMember
import com.pinakes.app.data.model.AttendanceVisit
import com.pinakes.app.data.model.StaffLedger
import com.pinakes.app.data.model.StaffLedgerSummary
import com.pinakes.app.data.model.StaffLoanLateFine
import com.pinakes.app.data.model.StaffPatronTypeRef
import com.pinakes.app.data.model.StaffUserDetail
import com.pinakes.app.data.model.UserImportField
import com.pinakes.app.data.model.UserImportJob
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.model.Meta
import com.pinakes.app.data.staff.AttendanceRules
import com.pinakes.app.data.staff.ChunkStep
import com.pinakes.app.data.staff.LateFineRules
import com.pinakes.app.data.staff.LedgerAction
import com.pinakes.app.data.staff.LedgerRules
import com.pinakes.app.data.staff.MoneyInput
import com.pinakes.app.data.staff.PatronTools
import com.pinakes.app.data.staff.Staff090Areas
import com.pinakes.app.data.staff.Staff090PatronsErrors
import com.pinakes.app.data.staff.UserImportRules
import com.pinakes.app.data.staff.VisitOutcome
import com.pinakes.app.ui.screens.movements.MoneyFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The pure decisions of the patron tools: money input, permissions, scan outcomes, import. */
class Staff090PatronsRulesTest {

    // ---- Money ----

    @Test fun amountsParseExactlyToCents() {
        assertEquals(MoneyInput.Parsed.Cents(29), MoneyInput.parse("0.29"))
        assertEquals(MoneyInput.Parsed.Cents(29), MoneyInput.parse("0,29"))
        assertEquals(MoneyInput.Parsed.Cents(1250), MoneyInput.parse(" 12.5 "))
        assertEquals(MoneyInput.Parsed.Cents(1200), MoneyInput.parse("12"))
        assertEquals(MoneyInput.Parsed.Cents(110), MoneyInput.parse("1.10"))
        // 4.35 * 100 is 434.99999 as a double: the string path never loses the cent.
        assertEquals(MoneyInput.Parsed.Cents(435), MoneyInput.parse("4.35"))
        assertEquals(MoneyInput.Parsed.Cents(99_999_999), MoneyInput.parse("999999.99"))
    }

    @Test fun badAmountsAreRefused() {
        assertEquals(MoneyInput.Parsed.Empty, MoneyInput.parse("  "))
        listOf("0", "0.00", "-1", "1.234", "1,000.00", "abc", "1.", ".5", "1e3").forEach {
            assertEquals(it, MoneyInput.Parsed.Invalid, MoneyInput.parse(it))
        }
        assertEquals(MoneyInput.Parsed.TooLarge, MoneyInput.parse("5.01", maxCents = 500))
        assertEquals(MoneyInput.Parsed.Cents(500), MoneyInput.parse("5", maxCents = 500))
    }

    @Test fun amountTextAndFormatting() {
        assertEquals("12.50", MoneyInput.text(1250))
        assertEquals("0.05", MoneyInput.text(5))
        assertEquals("€12.50", MoneyFormat.format(1250, "EUR", Locale.US))
        assertEquals("12,50 €", MoneyFormat.format(1250, "EUR", Locale.ITALY).replace(' ', ' '))
    }

    // ---- Ledger ----

    private val ledger = StaffLedger(balanceCents = 750, maxAmountCents = 99_999_999, canRecordPayment = true, canCharge = true, canWaive = true)

    @Test fun ledgerActionsFollowTheServerFlagsAndTheBalance() {
        assertEquals(listOf(LedgerAction.PAYMENT, LedgerAction.CHARGE, LedgerAction.WAIVER), LedgerRules.actions(ledger))
        // Nothing owed: only a charge makes sense.
        assertEquals(listOf(LedgerAction.CHARGE), LedgerRules.actions(ledger.copy(balanceCents = 0)))
        assertEquals(emptyList<LedgerAction>(), LedgerRules.actions(ledger.copy(canRecordPayment = false, canCharge = false, canWaive = false)))
        assertEquals(750, LedgerRules.maxCents(LedgerAction.PAYMENT, ledger))
        assertEquals(750, LedgerRules.maxCents(LedgerAction.WAIVER, ledger))
        assertEquals(99_999_999, LedgerRules.maxCents(LedgerAction.CHARGE, ledger))
    }

    @Test fun ledgerFormsNeedTheirText() {
        val ok = MoneyInput.Parsed.Cents(100)
        assertTrue(LedgerRules.canSubmit(LedgerAction.PAYMENT, ok, "contanti", ""))
        assertFalse(LedgerRules.canSubmit(LedgerAction.PAYMENT, ok, null, ""))
        assertFalse(LedgerRules.canSubmit(LedgerAction.CHARGE, ok, null, " "))
        assertTrue(LedgerRules.canSubmit(LedgerAction.WAIVER, ok, null, "Goodwill"))
        assertFalse(LedgerRules.canSubmit(LedgerAction.WAIVER, MoneyInput.Parsed.TooLarge, null, "Goodwill"))
    }

    // ---- Patron record ----

    private val allAreas = Staff090Areas(patrons = true, patronTypes = true, patronCards = true, patronPhotos = true, ledger = true)
    private val reader = StaffUserDetail(
        id = 7, cardNumber = "C-1", role = "standard", canEdit = true,
        patronType = StaffPatronTypeRef(1, "standard", "Standard"), ledger = StaffLedgerSummary(0, "EUR"),
    )

    @Test fun patronToolsShowOnlyWhatTheServerOffers() {
        val all = PatronTools.of(allAreas, reader)
        assertTrue(all.section && all.photoEdit && all.patronTypeEdit && all.renewCard && all.printCard && all.ledger)

        val readOnly = PatronTools.of(allAreas, reader.copy(canEdit = false))
        assertTrue(readOnly.photo && readOnly.patronType)
        assertFalse(readOnly.photoEdit || readOnly.patronTypeEdit)

        // Fines off (no ledger summary) and no patron types: those parts disappear.
        val bare = PatronTools.of(allAreas, reader.copy(ledger = null, patronType = null))
        assertFalse(bare.ledger || bare.patronType)

        // Staff and administrator accounts have no card to renew (409 card_not_renewable).
        assertFalse(PatronTools.of(allAreas, reader.copy(role = "admin")).renewCard)
        assertFalse(PatronTools.of(allAreas, reader.copy(cardNumber = null)).printCard)
        assertFalse(PatronTools.of(Staff090Areas.None, reader).section)
    }

    // ---- Return with a late fine ----

    @Test fun lateFineShowsOnlyWhenItWouldBeCharged() {
        val fine = StaffLoanLateFine("EUR", automaticFines = true, accruingDays = 4, accruingCents = 200)
        assertTrue(LateFineRules.shows(fine))
        assertFalse(LateFineRules.shows(fine.copy(accruingCents = 0)))
        assertFalse(LateFineRules.shows(fine.copy(automaticFines = false)))
        assertFalse(LateFineRules.shows(null))
        assertFalse(LateFineRules.canConfirm(waive = true, reason = "  "))
        assertTrue(LateFineRules.canConfirm(waive = false, reason = ""))
        assertEquals("First time", LateFineRules.waiveReason(fine, waive = true, reason = " First time "))
        assertNull(LateFineRules.waiveReason(fine, waive = false, reason = "x"))
        assertNull(LateFineRules.waiveReason(fine.copy(accruingCents = 0), waive = true, reason = "x"))
    }

    // ---- Visitor log ----

    @Test fun checkInOutcomesAndTones() {
        val welcome = AttendanceVisit(type = "member", member = AttendanceMember(shortName = "Marta P."))
        assertEquals(VisitOutcome.WELCOME, AttendanceRules.outcome(welcome))
        assertEquals(VisitOutcome.WARNING, AttendanceRules.outcome(welcome.copy(warning = "card_not_active")))
        assertEquals(VisitOutcome.WARNING, AttendanceRules.outcome(welcome.copy(member = AttendanceMember(cardExpired = true))))
        assertEquals(VisitOutcome.GUEST, AttendanceRules.outcome(AttendanceVisit(type = "guest")))
        assertEquals(VisitOutcome.UNKNOWN_CARD, AttendanceRules.outcome(ApiResult.Failure("card_not_found", "", 404)))
        assertEquals(VisitOutcome.ERROR, AttendanceRules.outcome(ApiResult.Failure("network", "")))
        assertTrue(AttendanceRules.successTone(VisitOutcome.WELCOME))
        assertTrue(AttendanceRules.successTone(VisitOutcome.GUEST))
        assertFalse(AttendanceRules.successTone(VisitOutcome.WARNING))
        assertFalse(AttendanceRules.successTone(VisitOutcome.UNKNOWN_CARD))
        assertEquals("PCAP 090A", AttendanceRules.normalizeCard("  PCAP 090A \n"))
    }

    @Test fun barsAndRetention() {
        assertEquals(listOf(0.5f, 1f, 0f), AttendanceRules.barFractions(listOf(2, 4, 0)))
        assertEquals(listOf(0f, 0f), AttendanceRules.barFractions(listOf(0, 0)))
        assertEquals(0, AttendanceRules.parseRetention("0", 3650))
        assertEquals(3650, AttendanceRules.parseRetention("3650", 3650))
        assertNull(AttendanceRules.parseRetention("3651", 3650))
        assertNull(AttendanceRules.parseRetention("", 3650))
    }

    // ---- Patron CSV import ----

    private val fields = listOf(UserImportField("nome", "First Name", true), UserImportField("cognome", "Last Name"), UserImportField("email", "Email", true))
    private val job = UserImportJob(id = "j", headers = listOf("A", "B", "C"), fields = fields, mapping = listOf("nome", "cognome"), totalRows = 25)

    @Test fun mappingEditor() {
        assertEquals(listOf("nome", "cognome", ""), UserImportRules.aligned(job))
        // A field moves to the column it is assigned to; "" frees a column.
        assertEquals(listOf("", "cognome", "nome"), UserImportRules.assign(listOf("nome", "cognome", ""), 2, "nome"))
        assertEquals(listOf("nome", "", ""), UserImportRules.assign(listOf("nome", "cognome", ""), 1, ""))
        assertEquals(listOf("email"), UserImportRules.missingRequired(fields, listOf("nome", "cognome", "")).map { it.key })
        assertFalse(UserImportRules.canStart(job, listOf("nome", "cognome", "")))
        assertTrue(UserImportRules.canStart(job, listOf("nome", "cognome", "email")))
        assertFalse(UserImportRules.canStart(job.copy(started = true), listOf("nome", "cognome", "email")))
    }

    @Test fun chunkDriverFollowsTheServer() {
        assertEquals(ChunkStep.Next(10), UserImportRules.next(job.copy(started = true, processed = 10)))
        assertEquals(ChunkStep.Done, UserImportRules.next(job.copy(status = "completed", complete = true)))
        assertEquals(ChunkStep.Done, UserImportRules.next(job.copy(status = "failed")))
        assertEquals(40, UserImportRules.percent(job.copy(processed = 10)))
        val ooo = ApiResult.Failure("out_of_order", "", 409, meta = Meta(processed = 20))
        assertEquals(ChunkStep.Next(20), UserImportRules.afterFailure(ooo, 10, 0))
        assertEquals(ChunkStep.Wait(10), UserImportRules.afterFailure(ApiResult.Failure("job_busy", "", 409), 10, 1))
        assertTrue(UserImportRules.afterFailure(ApiResult.Failure("not_started", "", 409), 0, 0) is ChunkStep.Stop)
        assertTrue(UserImportRules.afterFailure(ApiResult.Failure("gone", "", 410), 0, 0) is ChunkStep.Stop)
        assertTrue(UserImportRules.acceptsFileName("readers.CSV"))
        assertTrue(UserImportRules.acceptsFileName("readers.tsv"))
        assertFalse(UserImportRules.acceptsFileName("readers.xlsx"))
    }

    // ---- Errors ----

    @Test fun errorCodesMapToOurWording() {
        assertEquals(R.string.staff090_patrons_err_card_not_renewable, Staff090PatronsErrors.messageRes(ApiResult.Failure("card_not_renewable", "", 409)))
        assertEquals(R.string.staff090_patrons_err_amount_exceeds_balance, Staff090PatronsErrors.messageRes(ApiResult.Failure("amount_exceeds_balance", "", 409)))
        assertEquals(R.string.staff090_patrons_err_card_not_found, Staff090PatronsErrors.messageRes(ApiResult.Failure("card_not_found", "", 404)))
        assertEquals(R.string.staff090_patrons_err_file_missing, Staff090PatronsErrors.messageRes(ApiResult.Failure("file_missing", "", 409)))
        assertEquals(R.string.staff090_patrons_err_db_update_pending, Staff090PatronsErrors.messageRes(ApiResult.Failure("not_available", "", 503)))
        // The copy desk's 409 not_available keeps its own wording (StaffErrors).
        assertNull(Staff090PatronsErrors.messageRes(ApiResult.Failure("not_available", "", 409)))
        // A 422 validation carries the precise server text: never replaced.
        assertNull(Staff090PatronsErrors.messageRes(ApiResult.Failure("validation", "Enter the library card code.", 422)))
    }

    @Test fun aVisitToneSoundsOnce() {
        assertTrue(AttendanceRules.tonePending(tick = 1, played = 0))
        // Played, then the screen recomposes or rotates: nothing new to play.
        assertFalse(AttendanceRules.tonePending(tick = 1, played = 1))
        assertFalse(AttendanceRules.tonePending(tick = 0, played = 0))
        assertTrue(AttendanceRules.tonePending(tick = 2, played = 1))
    }
}
