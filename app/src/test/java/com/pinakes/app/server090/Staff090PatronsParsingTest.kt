package com.pinakes.app.server090

import com.pinakes.app.data.model.AttendanceCheckInRequest
import com.pinakes.app.data.model.AttendanceOverview
import com.pinakes.app.data.model.AttendancePurgeResult
import com.pinakes.app.data.model.AttendanceSettingsResult
import com.pinakes.app.data.model.AttendanceVisit
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.HealthPayload
import com.pinakes.app.data.model.LedgerChargeRequest
import com.pinakes.app.data.model.LedgerPaymentRequest
import com.pinakes.app.data.model.LedgerWriteResult
import com.pinakes.app.data.model.MetaEnvelope
import com.pinakes.app.data.model.PatronPhotoState
import com.pinakes.app.data.model.PatronType
import com.pinakes.app.data.model.PatronTypeRequest
import com.pinakes.app.data.model.PatronTypeUpdateMeta
import com.pinakes.app.data.model.PatronTypesMeta
import com.pinakes.app.data.model.ReturnRequest
import com.pinakes.app.data.model.StaffLedger
import com.pinakes.app.data.model.StaffLoan
import com.pinakes.app.data.model.StaffUser
import com.pinakes.app.data.model.StaffUserDetail
import com.pinakes.app.data.model.UserImportCancelResult
import com.pinakes.app.data.model.UserImportJob
import com.pinakes.app.data.model.UserImportStartRequest
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.parseErrorBody
import com.pinakes.app.data.staff.Staff090Areas
import com.pinakes.app.data.staff.Staff090PatronsErrors
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.R
import com.pinakes.app.ui.screens.staff.s090.patrons.toPatronsMessage
import com.pinakes.app.data.store.InstanceFeatures
import com.pinakes.app.data.staff.AttendanceRules
import com.pinakes.app.data.staff.VisitOutcome
import com.pinakes.app.ui.screens.staff.s090.patrons.toVisitorItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The patron tools' models against bodies captured from a real Pinakes 0.9.0-rc.1 server
 * (Mobile API 1.10.0, branch feat/mobile-staff-090): `src/test/resources/server090/patrons`.
 * Decoded with the app's own Json settings (NetworkModule.json).
 */
class Staff090PatronsParsingTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }

    private fun body(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/server090/patrons/$name.json")) { name }.bufferedReader().use { it.readText() }

    private inline fun <reified T> data(name: String): T = requireNotNull(json.decodeFromString<Envelope<T>>(body(name)).data) { name }

    private fun failure(name: String, status: Int): ApiResult.Failure = parseErrorBody(body(name), status)

    @Test fun healthAdvertisesThePatronAreas() {
        val health = data<HealthPayload>("health")
        val areas = Staff090Areas.of("admin", InstanceFeatures.from(health), online = true)
        assertTrue(areas.patrons && areas.patronTypes && areas.patronCards && areas.patronPhotos)
        assertTrue(areas.ledger && areas.attendance && areas.userImport)
    }

    @Test fun aServerThatTakesAPickedMemberSaysSo() {
        // Pinakes 0.9.2 (Mobile API 1.11.0): the visitor log also checks in a member by id.
        val areas = Staff090Areas.of("staff", InstanceFeatures.from(data<HealthPayload>("health_092")), online = true)
        assertTrue(areas.attendance && areas.attendanceMemberPick)
        // A 0.9.0 / 0.9.1 server has no flag: the search stays hidden.
        assertFalse(Staff090Areas.of("staff", InstanceFeatures.from(data<HealthPayload>("health")), online = true).attendanceMemberPick)
    }

    @Test fun aPickedMemberIsSentByIdAndRecordedLikeACard() {
        val app = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }
        assertEquals("""{"user_id":6805}""", app.encodeToString(AttendanceCheckInRequest.serializer(), AttendanceCheckInRequest(userId = 6805)))
        assertEquals("""{"card_code":"GB2026","branch_id":2}""", app.encodeToString(AttendanceCheckInRequest.serializer(), AttendanceCheckInRequest(cardCode = "GB2026", branchId = 2)))
        val visit = data<AttendanceVisit>("att_checkin_member")
        assertEquals("member", visit.type)
        assertEquals(6805, visit.member?.id)
        assertEquals(VisitOutcome.WELCOME, AttendanceRules.outcome(visit))
        val missing = failure("att_member_not_found", 404)
        assertEquals("member_not_found", missing.code)
        assertEquals(R.string.staff090_patrons_err_member_not_found, Staff090PatronsErrors.messageRes(missing))
        assertEquals(VisitOutcome.ERROR, AttendanceRules.outcome(missing))
        // Card and member together: the server's precise 422 text is kept.
        val both = failure("att_member_and_card", 422)
        assertEquals("validation", both.code)
        assertNull(Staff090PatronsErrors.messageRes(both))
    }

    @Test fun theEntranceSearchShowsCardAndMailWithoutLoanEligibility() {
        val blocked = data<List<StaffUser>>("users_search").single().copy(eligible = false, eligibilityReason = "debt_block")
        val item = blocked.toVisitorItem()
        assertEquals(blocked.id, item.id)
        assertTrue(item.enabled)
        assertNull(item.note)
        assertEquals(listOfNotNull(blocked.cardNumber, blocked.email).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }, item.subtitle)
    }

    @Test fun staffUserCarriesTheNewPatronFields() {
        val user = data<StaffUserDetail>("user_detail")
        assertEquals("PCAP090A", user.cardNumber)
        assertEquals("standard", user.patronType?.code)
        assertFalse(user.patronType!!.assigned)
        assertFalse(user.hasPhoto)
        assertNull(user.photoUrl)
        assertEquals(0, user.ledger?.balanceCents)
        assertEquals("EUR", user.ledger?.currency)
        assertTrue(user.canEdit)

        val blocked = data<StaffUserDetail>("user_detail_blocked")
        assertFalse(blocked.eligible)
        assertEquals("debt_block", blocked.eligibilityReason)
        assertEquals(1100, blocked.ledger?.balanceCents)
        assertTrue(blocked.ledger!!.debtBlocked)
        assertTrue(blocked.hasPhoto)
        assertTrue(blocked.photoUrl!!.contains("/staff/users/113143/photo?v="))

        val list = data<List<StaffUser>>("users_search")
        assertEquals("Standard", list.single().patronType?.name)
    }

    @Test fun patronTypesKeepTheirMeta() {
        val e = json.decodeFromString<MetaEnvelope<List<PatronType>, PatronTypesMeta>>(body("patron_types2"))
        assertEquals(2, e.data!!.size)
        assertTrue(e.data!![0].isDefault)
        assertEquals(30, e.data!![1].cardDurationDays)
        assertNull(e.data!![0].cardDurationDays)
        assertEquals(1, e.meta?.defaultId)
        assertTrue(e.meta!!.canAssign)
    }

    @Test fun patronTypeChangeAnswersTheUserAndTheExpiryFlag() {
        val e = json.decodeFromString<MetaEnvelope<StaffUser, PatronTypeUpdateMeta>>(body("set_type2"))
        assertEquals(3, e.data?.patronType?.id)
        assertTrue(e.data!!.patronType!!.assigned)
        assertEquals("2026-12-01", e.data?.cardExpiresAt)
        assertTrue(e.meta!!.cardExpiryRecomputed)
        // The default type is sent as 0, never as an omitted key (the server requires it).
        assertEquals("""{"patron_type_id":0}""", json.encodeToString(PatronTypeRequest.serializer(), PatronTypeRequest(0)))
        assertEquals("""{"patron_type_id":3,"recompute_card_expiry":true}""", json.encodeToString(PatronTypeRequest.serializer(), PatronTypeRequest(3, true)))
    }

    @Test fun renewAndPhotoAnswers() {
        assertEquals("2027-11-01", data<StaffUser>("renew").cardExpiresAt)
        assertEquals(true, json.decodeFromString<Envelope<StaffUser>>(body("renew_replay")).meta?.replayed)
        val photo = data<PatronPhotoState>("photo_upload")
        assertTrue(photo.hasPhoto)
        assertNotNull(photo.photoUrl)
        assertFalse(data<PatronPhotoState>("photo_delete").hasPhoto)
        val refused = failure("renew_admin", 409)
        assertEquals("card_not_renewable", refused.code)
    }

    @Test fun ledgerIsReadInFull() {
        val ledger = data<StaffLedger>("ledger_accrued")
        assertEquals(500, ledger.balanceCents)
        assertEquals(200, ledger.accruedCents)
        assertEquals(98228, ledger.accrued.single().loanId)
        assertEquals(4, ledger.accrued.single().daysLate)
        assertEquals(1000, ledger.debtBlockThresholdCents)
        assertTrue(ledger.automaticFines)
        assertEquals(listOf("contanti", "pos", "bonifico", "altro"), ledger.paymentMethods.map { it.code })
        assertEquals("Card (POS)", ledger.paymentMethods[1].label)
        assertEquals(99_999_999, ledger.maxAmountCents)
        assertTrue(ledger.canRecordPayment && ledger.canCharge && ledger.canWaive)

        val page = json.decodeFromString<Envelope<StaffLedger>>(body("ledger"))
        assertEquals(2, page.data!!.items.size)
        assertEquals(true, page.meta?.hasMore)
        assertNotNull(page.meta?.nextCursor)
        val waiver = page.data!!.items.first()
        assertEquals("condono", waiver.type)
        assertEquals(-250, waiver.amountCents)
        assertEquals("E2E Audit", waiver.operator?.name)
        val payment = page.data!!.items[1]
        assertTrue(payment.receiptAvailable)
        assertEquals("Card (POS)", payment.toMovement().paymentMethod)

        val last = json.decodeFromString<Envelope<StaffLedger>>(body("ledger_page2"))
        assertEquals(false, last.meta?.hasMore)
        assertEquals(12, last.data!!.items.last().id)
        // The balance card of the reader screen reads the same payload.
        assertEquals(500, ledger.toPayload().balanceTotalCents)
    }

    @Test fun ledgerWritesAndTheirRefusals() {
        val charge = data<LedgerWriteResult>("charge")
        assertEquals(1250, charge.movement.amountCents)
        assertEquals("addebito_manuale", charge.movement.type)
        assertEquals(1250, charge.balanceCents)
        val payment = data<LedgerWriteResult>("payment")
        assertEquals("pos", payment.movement.paymentMethod)
        assertEquals(750, payment.balanceCents)
        assertEquals(500, data<LedgerWriteResult>("waiver").balanceCents)

        val tooMuch = failure("payment_too_much", 409)
        assertEquals("amount_exceeds_balance", tooMuch.code)
        assertEquals("amount_exceeds_balance", tooMuch.meta?.reason)
        assertEquals("idempotency_conflict", failure("charge_conflict", 422).code)
        val noKey = failure("charge_nokey", 422)
        assertEquals("validation", noKey.code)
        assertEquals("idempotency_key", noKey.meta?.field)
        assertEquals("reason", failure("waiver_noreason", 422).meta?.field)

        assertEquals(
            """{"amount_cents":500,"method":"pos"}""",
            json.encodeToString(LedgerPaymentRequest.serializer(), LedgerPaymentRequest(500, "pos", null)),
        )
        assertEquals("""{"amount_cents":1250,"description":"x"}""", json.encodeToString(LedgerChargeRequest.serializer(), LedgerChargeRequest(1250, "x")))
    }

    @Test fun loanLateFineAndTheWaiverOnReturn() {
        val fine = data<StaffLoan>("loan_overdue").lateFine!!
        assertEquals("EUR", fine.currency)
        assertTrue(fine.automaticFines)
        assertEquals(4, fine.accruingDays)
        assertEquals(200, fine.accruingCents)
        assertEquals(0, fine.chargedCents)
        val returned = data<StaffLoan>("return_waived")
        assertEquals("restituito", returned.status)
        assertEquals(0, returned.lateFine!!.accruingCents)
        assertEquals("waive_reason", failure("return_noreason", 422).meta?.field)

        assertEquals(
            """{"outcome":"restituito","waive_fine":true,"waive_reason":"First time"}""",
            json.encodeToString(ReturnRequest.serializer(), ReturnRequest("restituito", waiveFine = true, waiveReason = "First time")),
        )
        // Without a waiver the body is the 1.7.0 one: no new keys.
        assertEquals("""{"outcome":"restituito"}""", json.encodeToString(ReturnRequest.serializer(), ReturnRequest("restituito")))
    }

    @Test fun visitorLogOverviewAndVisits() {
        val o = data<AttendanceOverview>("att_overview_branches")
        assertEquals(7, o.days)
        assertEquals(listOf(7, 30, 90, 365), o.periods)
        assertEquals(3, o.today.total)
        assertEquals(7, o.daily.size)
        assertEquals(24, o.hourly.size)
        assertEquals("Sede Pcap", o.branches.single().name)
        assertEquals(365, o.retention.days)
        assertEquals(3650, o.retention.maxDays)
        assertTrue(o.canConfigure)
        assertEquals("guest", o.recent[1].type)

        val ok = data<AttendanceVisit>("att_checkin")
        assertEquals("member", ok.type)
        assertEquals("Marta P.", ok.member?.shortName)
        assertTrue(ok.member!!.cardActive)
        assertNull(ok.warning)
        val warn = data<AttendanceVisit>("att_checkin_suspended")
        assertEquals("card_not_active", warn.warning)
        assertTrue(warn.member!!.cardExpired)
        assertEquals("Sede Pcap", warn.branch?.name)
        val guest = data<AttendanceVisit>("att_guest")
        assertEquals("Research", guest.guest?.reason)
        assertNull(guest.member)
        assertEquals("card_not_found", failure("att_unknown", 404).code)
        assertEquals("branch_id", failure("att_bad_branch", 422).meta?.field)

        assertEquals(400, data<AttendanceSettingsResult>("att_settings").retention.days)
        assertEquals("retention_days", failure("att_settings_bad", 422).meta?.field)
        assertEquals(0, data<AttendancePurgeResult>("att_purge").deleted)
    }

    @Test fun aRefusedActionIsNotPermittedAndKeepsTheStaffRole() {
        // A Staff account on an administrator's record and on the administrator-only settings.
        for (name in listOf("np_patron_type", "np_photo_delete", "np_purge", "np_settings")) {
            val f = failure(name, 403)
            assertEquals(name, "not_permitted", f.code)
            assertFalse(name, f.isForbidden())
            assertNull(name, Staff090PatronsErrors.messageRes(f))
        }
        assertEquals("Operation reserved for administrators.", failure("np_purge", 403).toPatronsMessage(R.string.staff_error_save).text)
        // The same operator reads the log, without the administrator's settings.
        assertFalse(data<AttendanceOverview>("np_overview").canConfigure)
    }

    @Test fun theOpenImportsList() {
        val e = json.decodeFromString<Envelope<List<UserImportJob>>>(body("ui_list"))
        // Newest first: the started one (readers-b) was uploaded last.
        assertEquals(listOf("readers-b.csv", "readers-a.csv"), e.data!!.map { it.fileName })
        assertTrue(e.data!![0].started)
        assertFalse(e.data!![1].started)
        assertEquals(2, e.data!![0].totalRows)
        assertEquals(false, e.meta?.hasMore)
        assertEquals(2, com.pinakes.app.data.staff.UserImportRules.openJobs(e.data!!).size)
        val page = json.decodeFromString<Envelope<List<UserImportJob>>>(body("ui_list_page"))
        assertEquals(1, page.data!!.size)
        assertEquals(true, page.meta?.hasMore)
        assertNotNull(page.meta?.nextCursor)
        // Cancelled jobs stay listed until they expire: none is offered again.
        val after = json.decodeFromString<Envelope<List<UserImportJob>>>(body("ui_list_after")).data!!
        assertEquals(listOf("cancelled", "cancelled"), after.map { it.status })
        assertTrue(com.pinakes.app.data.staff.UserImportRules.openJobs(after).isEmpty())
    }

    @Test fun patronImportJob() {
        val created = data<UserImportJob>("ui_upload")
        assertEquals("ready", created.status)
        assertFalse(created.started)
        assertEquals(4, created.totalRows)
        assertEquals(listOf("Nome", "Cognome", "Email", "Tessera"), created.headers)
        assertEquals(4, created.preview.size)
        assertEquals(listOf("nome", "email"), created.fields.filter { it.required }.map { it.key })
        assertEquals(listOf("nome", "cognome", "email", "codice_tessera"), created.mapping)
        assertNull(created.sendInvites)

        val done = data<UserImportJob>("ui_chunk")
        assertTrue(done.complete)
        assertEquals(4, done.processed)
        assertEquals(2, done.created)
        assertEquals(1, done.duplicates)
        assertEquals(1, done.failed)
        assertEquals("duplicate", done.errors[1].type)
        assertTrue(done.reportAvailable)
        assertEquals(false, done.sendInvites)

        assertEquals(0, failure("ui_chunk_ooo", 409).meta?.processed)
        assertEquals("not_started", failure("ui_chunk_notstarted", 409).code)
        assertEquals("gone", failure("ui_chunk_gone", 410).code)
        assertEquals("mapping", failure("ui_start_bad", 422).meta?.field)
        assertEquals("file", failure("ui_upload_bad", 422).meta?.field)
        assertTrue(data<UserImportCancelResult>("ui_cancel").cancelled)
        assertEquals(
            """{"mapping":["nome",""],"send_invites":true}""",
            json.encodeToString(UserImportStartRequest.serializer(), UserImportStartRequest(listOf("nome", ""), true)),
        )
    }
}
