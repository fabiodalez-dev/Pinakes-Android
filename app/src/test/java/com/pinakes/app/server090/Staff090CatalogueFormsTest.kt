package com.pinakes.app.server090

import com.pinakes.app.R
import com.pinakes.app.data.model.IdName
import com.pinakes.app.data.model.RdaEffective
import com.pinakes.app.data.model.StaffBook
import com.pinakes.app.data.model.StaffLookups
import com.pinakes.app.data.model.Supplier
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.parseErrorBody
import com.pinakes.app.data.repository.Staff090CataloguePaths
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.common.serverMessage
import com.pinakes.app.data.staff.BookCataloguing
import com.pinakes.app.data.staff.CataloguingModules
import com.pinakes.app.data.staff.CopyCatalogingRules
import com.pinakes.app.data.staff.CopyImportSubmission
import com.pinakes.app.data.staff.MarcImportRules
import com.pinakes.app.data.staff.Staff090CatalogueErrors
import com.pinakes.app.data.staff.StaffBookForm
import com.pinakes.app.data.staff.StaffBookWrite
import com.pinakes.app.data.staff.SubmissionKeys
import com.pinakes.app.data.staff.SupplierControls
import com.pinakes.app.data.staff.SupplierField
import com.pinakes.app.data.staff.SupplierForm
import com.pinakes.app.data.staff.SupplierProblem
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure rules of the cataloguing screens: forms, request bodies, limits, error wording. */
class Staff090CatalogueFormsTest {

    private val stored = Supplier(
        id = 21, name = "Libreria Cat090 Prova", contactPerson = "Mario Rossi", email = "ordini@example.org", phone = "+39 02 1234",
        website = "https://www.example.org", address = "Via Roma 1, Milano", vatNumber = "IT01234567890", notes = "Consegna il martedì",
        canEdit = true, canDelete = true,
    )

    // ---------- suppliers ----------

    @Test fun aSupplierNeedsANameAndAPlausibleEmail() {
        assertEquals(mapOf(SupplierField.NAME to SupplierProblem.REQUIRED), SupplierForm(name = "  ").validate())
        assertEquals(SupplierProblem.INVALID, SupplierForm(name = "A", email = "x@").validate()[SupplierField.EMAIL])
        assertEquals(SupplierProblem.INVALID, SupplierForm(name = "A", website = "not a url").validate()[SupplierField.WEBSITE])
        // A bare domain is fine: the server adds https://.
        assertTrue(SupplierForm(name = "A", email = "a@b.it", website = "www.example.org").valid)
    }

    @Test fun typedTextNeverExceedsTheColumn() {
        val form = SupplierForm().with(SupplierField.VAT_NUMBER, "X".repeat(30))
        assertEquals(20, form.vatNumber.length)
        assertEquals(255, SupplierField.NAME.maxLength)
    }

    @Test fun createSendsTheNameAndTheFilledFieldsTrimmed() {
        val body = SupplierForm(name = " Libreria ", email = "a@b.it ", notes = "").createJson()
        assertEquals(buildJsonObject { put("name", JsonPrimitive("Libreria")); put("email", JsonPrimitive("a@b.it")) }, body)
    }

    @Test fun updateSendsOnlyChangesAndNullForACleared() {
        val form = SupplierForm.from(stored).copy(phone = "", notes = "Aggiornato")
        assertEquals(buildJsonObject { put("phone", JsonNull); put("notes", JsonPrimitive("Aggiornato")) }, form.patchJson(stored))
        assertTrue(SupplierForm.from(stored).patchJson(stored).isEmpty())
    }

    @Test fun serverFieldKeysMapToFormFields() {
        assertEquals(SupplierField.VAT_NUMBER, SupplierForm.fieldOf("vat_number"))
        assertEquals(SupplierField.CONTACT_PERSON, SupplierForm.fieldOf("contact_person"))
        assertNull(SupplierForm.fieldOf("idempotency_key"))
    }

    @Test fun supplierControlsFollowTheServer() {
        assertEquals(SupplierControls(create = true, edit = true, delete = true), SupplierControls.of(true, stored))
        assertEquals(SupplierControls(create = false, edit = false, delete = false), SupplierControls.of(false, stored.copy(canEdit = false, canDelete = false)))
        assertFalse(SupplierControls.of(false, null).edit)
    }

    @Test fun aRetriedCreateKeepsItsIdempotencyKey() {
        var n = 0
        val keys = SubmissionKeys { "k${++n}" }
        val form = SupplierForm(name = "Libreria")
        assertEquals("k1", keys.keyFor(form))
        assertEquals("k1", keys.keyFor(form.copy()))
        assertEquals("k2", keys.keyFor(form.copy(phone = "1")))
    }

    // ---------- book form ----------

    private val lookupsOn = StaffLookups(
        suppliers = listOf(IdName(21, "Libreria"), IdName(22, "Donatore")),
        rdaContentTypes = listOf(JsonPrimitive("txt")), rdaMediaTypes = listOf(JsonPrimitive("n")), rdaCarrierTypes = listOf(JsonPrimitive("nc")),
    )

    private val book = StaffBook(
        id = 9, title = "Libro", supplierId = 21, supplier = IdName(21, "Libreria"),
        rdaContentType = "spw", rdaMediaType = null, rdaCarrierType = "cd", rdaEffective = RdaEffective("spw", "n", "cd"),
    )

    @Test fun modulesComeFromTheLookups() {
        assertEquals(CataloguingModules(suppliers = true, rda = true), CataloguingModules.from(lookupsOn))
        assertEquals(CataloguingModules.Off, CataloguingModules.from(StaffLookups()))
        assertEquals(CataloguingModules(suppliers = true, rda = false), CataloguingModules.from(StaffLookups(suppliers = emptyList())))
    }

    @Test fun theFormReadsTheBooksValues() {
        assertEquals(BookCataloguing(supplierId = 21, rdaContentType = "spw", rdaMediaType = null, rdaCarrierType = "cd"), StaffBookForm.from(book).cataloguing)
    }

    @Test fun createSendsSupplierAndOverridesOnlyWithTheModulesOn() {
        val form = StaffBookForm(title = "Libro", cataloguing = BookCataloguing(supplierId = 21, rdaContentType = "spw"))
        val on = StaffBookWrite.create(form, CataloguingModules.from(lookupsOn))
        assertEquals(JsonPrimitive(21), on["supplier_id"])
        assertEquals(JsonPrimitive("spw"), on["rda_content_type"])
        assertFalse("derived is never sent on create", on.containsKey("rda_media_type"))
        val off = StaffBookWrite.create(form)
        assertFalse(off.containsKey("supplier_id"))
        assertFalse(off.containsKey("rda_content_type"))
    }

    @Test fun updateSendsChangesAndNullToClear() {
        val modules = CataloguingModules.from(lookupsOn)
        val form = StaffBookForm.from(book).let { it.copy(cataloguing = it.cataloguing.copy(supplierId = 22, rdaContentType = null)) }
        val patch = StaffBookWrite.update(book, form, modules)
        assertEquals(JsonPrimitive(22), patch["supplier_id"])
        assertEquals(JsonNull, patch["rda_content_type"])
        assertFalse(patch.containsKey("rda_carrier_type"))
        assertFalse(patch.containsKey("title"))
        // Unchanged: an empty patch, so the screen does not call the server at all.
        assertTrue(StaffBookWrite.update(book, StaffBookForm.from(book), modules).isEmpty())
        // Modules off: the keys stay out even when the values differ.
        assertTrue(StaffBookWrite.update(book, form, CataloguingModules.Off).isEmpty())
        val cleared = StaffBookWrite.update(book, StaffBookForm.from(book).let { it.copy(cataloguing = it.cataloguing.copy(supplierId = null)) }, modules)
        assertEquals(JsonNull, cleared["supplier_id"])
    }

    // ---------- MARC ----------

    @Test fun marcFileNamesAndEncoding() {
        assertTrue(MarcImportRules.acceptsFileName("export.MRC", emptyList()))
        assertTrue(MarcImportRules.acceptsFileName("records.xml", listOf("mrc", "xml")))
        assertFalse(MarcImportRules.acceptsFileName("books.csv", listOf("mrc", "xml")))
        assertFalse(MarcImportRules.acceptsFileName("noextension", emptyList()))
        assertTrue(MarcImportRules.encodingApplies("a.mrc"))
        assertTrue(MarcImportRules.encodingApplies(null))
        assertFalse(MarcImportRules.encodingApplies("a.marcxml"))
    }

    @Test fun downloadPaths() {
        assertEquals("staff/books/9/catalogue-card.pdf", Staff090CataloguePaths.catalogueCard(9))
        assertEquals("staff/books/catalogue-cards.pdf?ids=3,1", Staff090CataloguePaths.catalogueCards(listOf(3, 0, 1, 3)))
        assertNull(Staff090CataloguePaths.catalogueCards(listOf(0, -1)))
        assertEquals(200, Staff090CataloguePaths.catalogueCards((1..250).toList())!!.substringAfter("ids=").split(',').size)
        assertEquals("staff/marc/export?format=marcxml", Staff090CataloguePaths.marcExport("marcxml", null))
        assertEquals("staff/marc/export?format=marc21&ids=5,6", Staff090CataloguePaths.marcExport("pdf", listOf(5, 6, 5)))
        assertNull(Staff090CataloguePaths.marcExport("marc21", emptyList()))
        assertNull(Staff090CataloguePaths.marcExport("marc21", (1..1001).toList()))
        assertEquals("application/marcxml+xml", Staff090CataloguePaths.exportMime("marcxml"))
        assertEquals("application/marc", Staff090CataloguePaths.exportMime("unimarc"))
    }

    // ---------- copy cataloguing ----------

    @Test fun searchNeedsACatalogueATermAndAValidMax() {
        assertTrue(CopyCatalogingRules.canSearch(setOf("local"), "Eco", 10))
        assertFalse(CopyCatalogingRules.canSearch(emptySet(), "Eco", 10))
        assertFalse(CopyCatalogingRules.canSearch(setOf("local"), "  ", 10))
        assertFalse(CopyCatalogingRules.canSearch(setOf("local"), "Eco", 26))
        assertFalse(CopyCatalogingRules.canSearch(setOf("local"), "x".repeat(201), 10))
    }

    @Test fun selectionStopsAtTheImportLimit() {
        val two = CopyCatalogingRules.toggle(CopyCatalogingRules.toggle(emptySet(), "a", limit = 2), "b", limit = 2)
        assertEquals(setOf("a", "b"), two)
        assertEquals(two, CopyCatalogingRules.toggle(two, "c", limit = 2))
        assertEquals(setOf("b"), CopyCatalogingRules.toggle(two, "a", limit = 2))
    }

    @Test fun aScannedBarcodeBecomesASearch() {
        assertEquals("isbn" to "9788806207823", CopyCatalogingRules.fromScan("9788806207823"))
        // ISSN 0317-8471 printed as an EAN-13 (977 + 7 digits + 00 + check).
        assertEquals("issn" to "0317-8471", CopyCatalogingRules.fromScan("9770317847001"))
        assertEquals("any" to "4006381333931", CopyCatalogingRules.fromScan("4006381333931"))
        assertNull(CopyCatalogingRules.fromScan("9788806207824")) // bad check digit
        assertNull(CopyCatalogingRules.fromScan("12345"))
        assertEquals("2434-561X", CopyCatalogingRules.issnOf("2434561"))
    }

    @Test fun theSameSelectionIsTheSameSubmission() {
        val keys = SubmissionKeys()
        val first = keys.keyFor(CopyImportSubmission("s", listOf("a", "b"), "skip", false))
        assertEquals(first, keys.keyFor(CopyImportSubmission("s", listOf("a", "b"), "skip", false)))
        assertNotEquals(first, keys.keyFor(CopyImportSubmission("s", listOf("a", "b"), "overwrite", false)))
    }

    // ---------- errors ----------

    @Test fun cataloguingCodesHaveOurWording() {
        fun f(code: String, status: Int) = parseErrorBody("""{"data":null,"meta":{},"error":{"code":"$code","message":"Server text"}}""", status)
        assertEquals(R.string.staff090_catalogue_err_schema_missing, Staff090CatalogueErrors.messageRes(f("schema_missing", 409)))
        assertEquals(R.string.staff090_catalogue_err_copy_unavailable, Staff090CatalogueErrors.messageRes(f("copy_cataloging_unavailable", 404)))
        assertEquals(R.string.staff090_catalogue_err_job_cancelled, Staff090CatalogueErrors.messageRes(f("job_cancelled", 409)))
        // 410: an expired search for copy cataloguing, the import's own wording elsewhere.
        assertEquals(R.string.staff090_catalogue_err_search_gone, Staff090CatalogueErrors.messageRes(f("gone", 410), copyCataloguing = true))
        assertNull(Staff090CatalogueErrors.messageRes(f("gone", 410)))
        // A validation error keeps the server's text, which names the field.
        assertNull(Staff090CatalogueErrors.messageRes(f("validation", 422)))
        assertNull(Staff090CatalogueErrors.messageRes(ApiResult.Failure("network", "x")))
    }

    @Test fun notPermittedShowsTheServerTextAndKeepsTheRole() {
        val f = parseErrorBody("""{"data":null,"meta":{},"error":{"code":"not_permitted","message":"Permesso mancante."}}""", 403)
        assertNull(Staff090CatalogueErrors.messageRes(f))
        assertFalse(f.isForbidden())
        assertEquals("Permesso mancante.", f.serverMessage())
        assertTrue(parseErrorBody("""{"data":null,"meta":{},"error":{"code":"forbidden","message":"x"}}""", 403).isForbidden())
    }

    @Test fun unavailabilityReason() {
        assertEquals(R.string.staff090_catalogue_copy_reason_plugin, Staff090CatalogueErrors.copyCatalogingReasonRes("plugin_inactive"))
        assertEquals(R.string.staff090_catalogue_err_copy_unavailable, Staff090CatalogueErrors.copyCatalogingReasonRes(null))
        assertEquals(R.string.staff090_catalogue_err_copy_unavailable, Staff090CatalogueErrors.copyCatalogingReasonRes("something_new"))
    }
}
