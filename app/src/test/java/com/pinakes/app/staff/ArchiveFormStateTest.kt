package com.pinakes.app.staff

import com.pinakes.app.data.model.IdTitle
import com.pinakes.app.data.model.StaffArchiveDetail
import com.pinakes.app.data.model.StaffOffer
import com.pinakes.app.data.staff.ArchiveField
import com.pinakes.app.data.staff.ArchiveForm
import com.pinakes.app.data.staff.AuthorityForm
import com.pinakes.app.ui.screens.staff.plugins.OfferActions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The archive and authority forms mirror the plugin's payload and rules; offer actions per status. */
class ArchiveFormStateTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }

    @Test fun newFormUsesThePluginDefaultsAndNeedsTheRequiredFields() {
        val form = ArchiveForm.new()
        assertEquals("PINAKES", form["institution_code"])
        assertEquals("ita", form["language_codes"])
        assertEquals(setOf(ArchiveField.REFERENCE_CODE, ArchiveField.CONSTRUCTED_TITLE), form.errors())
        val ok = form.with("reference_code", "IT-1").with("constructed_title", "Fondo Rossi")
        assertTrue(ok.errors().isEmpty())
    }

    @Test fun yearsMustBeNumbersInOrder() {
        val base = ArchiveForm.new().with("reference_code", "R").with("constructed_title", "T")
        assertEquals(setOf(ArchiveField.DATE_START), base.with("date_start", "1920s").errors())
        assertEquals(setOf(ArchiveField.DATE_END), base.with("date_start", "1950").with("date_end", "1940").errors())
        assertTrue(base.with("date_start", "1940").with("date_end", "1950").errors().isEmpty())
        assertEquals(setOf(ArchiveField.PARENT_ID), base.with("parent_id", "abc").errors())
    }

    @Test fun jsonSendsEveryKeyWithNumbersAndNulls() {
        val body = ArchiveForm.new().with("reference_code", " IT-1 ").with("constructed_title", "T").with("date_start", "1920").copy(published = false).toJson()
        assertEquals(ArchiveForm.TEXT_KEYS.size + ArchiveForm.INT_KEYS.size + 1, body.size)
        assertEquals("IT-1", body["reference_code"]!!.jsonPrimitive.content)
        assertEquals(1920, body["date_start"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, body["date_end"])
        assertEquals(JsonPrimitive(""), body["extent"])
        assertFalse(body["published"]!!.jsonPrimitive.boolean)
    }

    @Test fun formReadsTheDetailColumns() {
        val detail = json.decodeFromString<StaffArchiveDetail>(
            """{"id":3,"reference_code":"IT-1","level":"series","published":false,
               "fields":{"reference_code":"IT-1","level":"series","constructed_title":"Lettere","date_start":1920,"date_end":null,"extent":"2 buste"}}"""
        )
        val form = ArchiveForm.from(detail)
        assertEquals("Lettere", form["constructed_title"])
        assertEquals("1920", form["date_start"])
        assertEquals("", form["date_end"])
        assertFalse(form.published)
        assertTrue(form.errors().isEmpty())
    }

    @Test fun authorityFormNeedsTypeAndName() {
        assertFalse(AuthorityForm().valid)
        val a = AuthorityForm().with("authorised_form", "Rossi, Mario")
        assertTrue(a.valid)
        assertEquals(AuthorityForm.KEYS.size, a.toJson().size)
        assertEquals("person", a.toJson()["type"]!!.jsonPrimitive.content)
    }

    @Test fun offerActionsByStatus() {
        val pending = StaffOffer(id = 1, status = "pending")
        assertTrue(OfferActions.canAccept(pending) && OfferActions.canReject(pending) && OfferActions.canReceive(pending))
        val accepted = pending.copy(status = "accepted")
        assertFalse(OfferActions.canAccept(accepted))
        assertTrue(OfferActions.canReceive(accepted))
        listOf("received", "rejected").forEach { s ->
            val o = pending.copy(status = s)
            assertFalse(OfferActions.canAccept(o) || OfferActions.canReject(o) || OfferActions.canReceive(o))
        }
        assertTrue(OfferActions.needsBook(pending))
        assertFalse(OfferActions.needsBook(pending.copy(book = IdTitle(5, "Orlando"))))
    }
}
