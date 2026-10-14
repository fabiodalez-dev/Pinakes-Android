package com.pinakes.app.staff

import com.pinakes.app.data.model.CirculationSummary
import com.pinakes.app.data.model.CopyLookup
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.HealthPayload
import com.pinakes.app.data.model.ImportJob
import com.pinakes.app.data.model.LoanCreateRequest
import com.pinakes.app.data.model.StaffArchiveDetail
import com.pinakes.app.data.model.StaffLoan
import com.pinakes.app.data.model.StaffLookups
import com.pinakes.app.data.model.StaffSeries
import com.pinakes.app.data.model.StaffUserDetail
import com.pinakes.app.data.network.parseErrorBody
import com.pinakes.app.data.staff.LoanAction
import com.pinakes.app.data.staff.LoanActions
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Mobile API 1.7.0 payloads decode tolerantly: defaults for absent keys, unknown values ignored. */
class StaffCirculationModelsParsingTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }

    @Test fun healthStaffFlagsDefaultOffAndParseWhenSent() {
        val old = json.decodeFromString<HealthPayload>("""{"features":{"staff_catalog":true}}""")
        assertFalse(old.features.staffCirculation || old.features.staffCopies || old.features.staffArchives || old.features.staffLibraryThing)
        val new = json.decodeFromString<HealthPayload>(
            """{"features":{"staff_circulation":true,"staff_copies":true,"staff_series":true,"staff_merges":true,"staff_labels":true,
               "staff_imports":true,"staff_covers":true,"staff_desiderata":true,"staff_archives":true,"staff_periodicals":true,
               "staff_bookclub":true,"staff_frbr":true,"staff_digital_library":true,"staff_librarything":true}}"""
        )
        with(new.features) {
            assertTrue(staffCirculation && staffCopies && staffSeries && staffMerges && staffLabels && staffImports && staffCovers)
            assertTrue(staffDesiderata && staffArchives && staffPeriodicals && staffBookClub && staffFrbr && staffDigitalLibrary && staffLibraryThing)
        }
    }

    @Test fun staffLoanFullShape() {
        val loan = json.decodeFromString<StaffLoan>(
            """{"id":7,"status":"in_ritardo","status_label":"In ritardo","origin":"diretto",
               "book":{"id":5,"title":"Orlando","cover_url":"/c.jpg","isbn13":"9780156701600"},
               "copy":{"id":11,"inventory_number":"INV-0011","status":"prestato"},
               "user":{"id":3,"name":"Ada Rossi","email":"ada@example.org","card_number":"C-12","status":"attivo"},
               "processed_by":{"id":1,"name":"Admin"},"loaned_at":"2026-09-01","due_at":"2026-10-01",
               "days_overdue":13,"renewals":1,"max_renewals":2,"penalty":"4.50","recall_count":2,
               "actions":["return","edit","recall","receipt","teleport"]}"""
        )
        assertEquals(7, loan.id)
        assertEquals("INV-0011", loan.copy?.inventoryNumber)
        assertEquals("C-12", loan.user.cardNumber)
        assertEquals(4.5, loan.penalty!!, 0.0001)
        assertEquals(13, loan.daysOverdue)
        assertEquals(listOf(LoanAction.RETURN, LoanAction.EDIT, LoanAction.RECALL, LoanAction.RECEIPT), LoanActions.of(loan))
    }

    @Test fun staffLoanMinimalShapeUsesDefaults() {
        val loan = json.decodeFromString<StaffLoan>("""{"id":1,"status":"pendente"}""")
        assertNull(loan.copy)
        assertEquals("", loan.book.title)
        assertEquals(0, loan.renewals)
        assertTrue(loan.actions.isEmpty())
        assertFalse(loan.returnedLate)
    }

    @Test fun summaryAndUserDetail() {
        val s = json.decodeFromString<Envelope<CirculationSummary>>(
            """{"data":{"pending":2,"pickup_ready":1,"scheduled":0,"active":5,"overdue":3,"reservations":4,"due_today":1,
               "returned_today":2,"today":"2026-10-14","settings":{"loan_duration_days":30,"max_renewals":2}}}"""
        ).data!!
        assertEquals(3, s.overdue)
        assertEquals(30, s.settings?.loanDurationDays)
        val u = json.decodeFromString<StaffUserDetail>(
            """{"id":3,"name":"Ada","status":"sospeso","eligible":false,"eligibility_reason":"user_suspended",
               "loans":[{"id":7,"status":"in_corso"}],"reservations":[{"id":2,"status":"attiva","queue_position":1}]}"""
        )
        assertFalse(u.eligible)
        assertEquals(1, u.loans.size)
        assertEquals(1, u.reservations.first().queuePosition)
    }

    @Test fun copyLookupFoundAndNotFound() {
        val found = json.decodeFromString<CopyLookup>(
            """{"found":true,"copy":{"id":11,"inventory_number":"INV-0011","status":"prestato","held":false,"deletable":false,
               "book":{"id":5,"title":"Orlando"},"available":false,"loan":{"id":7,"status":"in_corso","user":{"id":3,"name":"Ada"}}}}"""
        )
        assertTrue(found.found)
        assertEquals(7, found.copy?.loan?.id)
        assertEquals(false, found.copy?.available)
        val missing = json.decodeFromString<CopyLookup>("""{"found":false,"copy":null}""")
        assertFalse(missing.found)
        assertNull(missing.copy)
    }

    @Test fun createRequestAlwaysCarriesImmediate() {
        val body = json.encodeToString(LoanCreateRequest.serializer(), LoanCreateRequest(userId = 3, bookId = 5, immediate = false))
        assertTrue(body, body.contains("\"immediate\":false"))
        assertFalse(body, body.contains("copy_code"))
    }

    @Test fun errorMetaCarriesFieldProcessedAndRetry() {
        val f = parseErrorBody("""{"data":null,"meta":{"field":"due_at","processed":20,"retry_after":30},"error":{"code":"out_of_order","message":"x"}}""", 409)
        assertEquals("out_of_order", f.code)
        assertEquals("due_at", f.meta?.field)
        assertEquals(20, f.meta?.processed)
        assertEquals(30, f.meta?.retryAfter)
    }

    @Test fun lookupsEditableCopyStates() {
        val withFlag = json.decodeFromString<StaffLookups>(
            """{"copy_states":[{"value":"disponibile","label":"Disponibile","editable":true},{"value":"prestato","label":"In prestito","editable":false},
               {"value":"perso","label":"Perso","editable":true}],"return_outcomes":[{"value":"restituito","label":"Restituito"}]}"""
        )
        assertEquals(listOf("disponibile", "perso"), withFlag.editableCopyStateOptions.map { it.value })
        assertEquals("restituito", withFlag.returnOutcomeOptions.single().value)
        val older = json.decodeFromString<StaffLookups>("""{"copy_states":["disponibile","prestato","prenotato","manutenzione"]}""")
        assertEquals(listOf("disponibile", "manutenzione"), older.editableCopyStateOptions.map { it.value })
    }

    @Test fun contentAndPluginShapes() {
        val series = json.decodeFromString<StaffSeries>(
            """{"id":4,"name":"Discworld","type":"serie","book_count":41,"min_number":1,"max_number":"41","complete":true,
               "children":[{"id":5,"name":"Rincewind","parent_id":4}]}"""
        )
        assertEquals("1", series.minNumber)
        assertEquals(5, series.children.single().id)
        val job = json.decodeFromString<ImportJob>(
            """{"id":"6f1c","kind":"csv","file_name":"books.csv","total_rows":30,"processed":10,"errors":2,
               "error_details":["Row 3: missing title",{"message":"Row 9: bad ISBN"}]}"""
        )
        assertEquals(listOf("Row 3: missing title", "Row 9: bad ISBN"), job.errorLines)
        assertFalse(job.complete)
        val archive = json.decodeFromString<StaffArchiveDetail>(
            """{"id":3,"title":"Fondo X","published":false,"fields":{"reference_code":"IT-1","date_start":1920,"extent":null},
               "files":[{"id":2,"url":"/a.pdf","mime":"application/pdf"}],"authorities":[{"id":9,"name":"Rossi","role":"creator"}]}"""
        )
        assertEquals("IT-1", archive.field("reference_code"))
        assertEquals("1920", archive.field("date_start"))
        assertEquals("", archive.field("extent"))
        assertEquals("", archive.field("missing"))
        assertFalse(archive.published)
    }
}
