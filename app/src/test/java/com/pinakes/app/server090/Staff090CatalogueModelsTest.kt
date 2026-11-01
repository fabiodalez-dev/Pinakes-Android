package com.pinakes.app.server090

import com.pinakes.app.data.model.CopyCatalogingImport
import com.pinakes.app.data.model.CopyCatalogingSearch
import com.pinakes.app.data.model.CopyCatalogingServers
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.Envelope090
import com.pinakes.app.data.model.HealthPayload
import com.pinakes.app.data.model.MarcCancelResult
import com.pinakes.app.data.model.MarcImportJob
import com.pinakes.app.data.model.MarcOptions
import com.pinakes.app.data.model.StaffBook
import com.pinakes.app.data.model.StaffBookRow
import com.pinakes.app.data.model.StaffLookups
import com.pinakes.app.data.model.Supplier
import com.pinakes.app.data.model.SupplierDeleteResult
import com.pinakes.app.data.model.fieldErrors
import com.pinakes.app.data.model.metaBoolean
import com.pinakes.app.data.network.parseErrorBody
import com.pinakes.app.data.repository.Staff090CatalogueRepository
import com.pinakes.app.data.staff.ChunkStep
import com.pinakes.app.data.staff.ImportProgress
import com.pinakes.app.data.staff.StaffAreas
import com.pinakes.app.data.store.InstanceFeatures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cataloguing payloads of Mobile API 1.10.0 decode with the app's Json settings. Every
 * sample under `resources/server090/catalogue` is a body the slot server (Pinakes 0.9.0-rc.1,
 * branch feat/mobile-staff-090) answered to curl on 2026-11-01.
 */
class Staff090CatalogueModelsTest {
    // Same configuration as NetworkModule.json.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }

    private fun sample(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/server090/catalogue/$name.json")) { name }.bufferedReader().use { it.readText() }

    private inline fun <reified T> data(name: String): T = requireNotNull(json.decodeFromString<Envelope090<T>>(sample(name)).data)

    // ---------- suppliers ----------

    @Test fun supplierListCarriesCanCreateInItsMeta() {
        val envelope = json.decodeFromString<Envelope090<List<Supplier>>>(sample("suppliers"))
        val s = envelope.data!!.single()
        assertEquals(21, s.id)
        assertEquals("Libreria Cat090 Prova", s.name)
        assertEquals("https://www.example.org", s.website) // the server adds the scheme
        assertEquals("IT01234567890", s.vatNumber)
        assertEquals(0, s.bookCount)
        assertTrue(s.canEdit && s.canDelete)
        assertEquals(true, envelope.meta.metaBoolean("can_create"))
    }

    @Test fun createdUpdatedAndReplayedSuppliers() {
        val created = data<Supplier>("supplier_created")
        assertEquals("Mario Rossi", created.contactPerson)
        assertEquals("+39 02 1234", created.phone)
        val updated = data<Supplier>("supplier_updated")
        assertNull(updated.phone) // null cleared it
        assertEquals("Aggiornato", updated.notes)
        val replay = json.decodeFromString<Envelope<Supplier>>(sample("supplier_replayed"))
        assertEquals(true, replay.meta?.replayed)
        assertEquals(created.id, replay.data?.id)
    }

    @Test fun supplierValidationErrorsAreReadFieldByField() {
        val body = sample("supplier_422")
        val failure = parseErrorBody(body, 422)
        assertEquals("validation", failure.code)
        assertEquals("vat_number", failure.meta?.field)
        val fields = Staff090CatalogueRepository.metaOf(json, body).fieldErrors()
        assertEquals(setOf("vat_number", "name", "email", "website"), fields.keys)
        assertTrue(fields.getValue("name").isNotBlank())
    }

    @Test fun supplierDeleteSaysHowManyBooksLostTheLink() {
        val d = data<SupplierDeleteResult>("supplier_deleted")
        assertEquals(21, d.id)
        assertTrue(d.deleted)
        assertEquals(2, d.booksUnlinked)
    }

    // ---------- book fields ----------

    @Test fun lookupsCarryTheSupplierAndRdaListsWhileTheModulesAreOn() {
        val l = json.decodeFromString<Envelope<StaffLookups>>(sample("lookups_full")).data!!
        assertEquals("Libreria Cat090 Prova", l.suppliers!!.single { it.id == 21 }.name)
        assertEquals("spw", StaffLookups.options(l.rdaContentTypes!!)[1].value)
        assertTrue(StaffLookups.options(l.rdaMediaTypes!!).any { it.value == "s" })
        assertTrue(StaffLookups.options(l.rdaCarrierTypes!!).any { it.value == "nc" })
        // A server with the modules off sends none of the keys.
        val off = json.decodeFromString<StaffLookups>("""{"languages":[]}""")
        assertNull(off.suppliers)
        assertNull(off.rdaContentTypes)
    }

    @Test fun staffBookReadsSupplierRdaOverridesAndTheEffectiveTriple() {
        val b = json.decodeFromString<Envelope<StaffBook>>(sample("book_created")).data!!
        assertEquals(21, b.supplierId)
        assertEquals("Libreria Cat090 Prova", b.supplier?.name)
        assertEquals("spw", b.rdaContentType)
        assertNull(b.rdaMediaType)
        assertEquals("cd", b.rdaCarrierType)
        assertEquals("n", b.rdaEffective?.mediaType) // derived from tipo_media = libro
        assertEquals("spw", b.rdaEffective?.contentType)
        val detail = json.decodeFromString<Envelope<StaffBook>>(sample("book_detail")).data!!
        assertEquals(b.id, detail.id)
        assertEquals(21, detail.supplierId)
    }

    @Test fun bookListFilteredBySupplier() {
        val e = json.decodeFromString<Envelope<List<StaffBookRow>>>(sample("books_by_supplier"))
        assertEquals(listOf(338681), e.data!!.map { it.id })
        assertEquals(false, e.meta?.hasMore)
    }

    // ---------- MARC ----------

    @Test fun marcOptions() {
        val o = data<MarcOptions>("marc_options")
        assertEquals(listOf("auto", "marc21", "unimarc", "danmarc2"), o.flavours.map { it.value })
        assertEquals(listOf("skip", "fill", "overwrite"), o.duplicateModes.map { it.value })
        assertTrue(o.duplicateModes.all { it.description.isNotBlank() })
        assertEquals(listOf("marc21", "marcxml", "unimarc"), o.exportFormats.map { it.value })
        assertEquals(20L * 1024 * 1024, o.maxFileBytes)
        assertEquals(1000, o.maxExportIds)
        assertEquals(21, o.suppliers!!.single().id)
        assertTrue(o.copyCataloging.available)
        assertNull(o.copyCataloging.reason)
        val off = data<MarcOptions>("marc_options_copy_unavailable").copyCataloging
        assertFalse(off.available)
        assertEquals("plugin_inactive", off.reason)
        // Suppliers module off: null, not an empty list.
        assertNull(json.decodeFromString<MarcOptions>("""{"suppliers":null}""").suppliers)
    }

    @Test fun uploadedJobShowsItsPreviewAndIsNotFinished() {
        val job = data<MarcImportJob>("marc_job_ready")
        assertEquals("marc", job.kind)
        assertEquals("ready", job.status)
        assertEquals(1, job.totalRows)
        assertEquals(21, job.supplierId)
        assertEquals("iso2709", job.container)
        val row = job.preview.single()
        assertEquals("Libro Cat090 Prova", row.title)
        assertEquals("marc21", row.flavour)
        assertNull(row.existingId)
        assertNull(row.error)
        // The CSV loop drives it unchanged.
        assertFalse(ImportProgress.isFinished(job.asImportJob()))
        assertEquals(ChunkStep.Next(0), ImportProgress.next(job.asImportJob()))
        assertEquals(0, ImportProgress.percent(job.asImportJob()))
    }

    @Test fun completedJobEndsTheLoop() {
        val job = data<MarcImportJob>("marc_job_completed")
        assertTrue(job.complete)
        assertEquals(1, job.imported)
        assertEquals(0, job.skipped)
        assertEquals("fcba5bd46c2dbafb057abe13d8615218e30c", job.importId)
        assertTrue(ImportProgress.isFinished(job.asImportJob()))
        assertEquals(ChunkStep.Done, ImportProgress.next(job.asImportJob()))
        assertEquals(100, ImportProgress.percent(job.asImportJob()))
        assertTrue(job.errorLines.isEmpty())
    }

    @Test fun outOfOrderResumesFromTheServersPositionAndGoneStops() {
        val outOfOrder = parseErrorBody(sample("marc_chunk_409"), 409)
        assertEquals("out_of_order", outOfOrder.code)
        assertEquals(ChunkStep.Next(0), ImportProgress.afterFailure(outOfOrder, start = 1, busyAttempts = 0))
        val gone = parseErrorBody(sample("marc_chunk_410"), 410)
        assertEquals("gone", gone.code)
        assertTrue(ImportProgress.afterFailure(gone, start = 1, busyAttempts = 0) is ChunkStep.Stop)
        val cancelled = parseErrorBody("""{"data":null,"meta":{"processed":4},"error":{"code":"job_cancelled","message":"x"}}""", 409)
        assertTrue(ImportProgress.afterFailure(cancelled, start = 4, busyAttempts = 0) is ChunkStep.Stop)
    }

    @Test fun uploadValidationAndCancel() {
        val body = sample("marc_upload_422")
        assertEquals("flavour", parseErrorBody(body, 422).meta?.field)
        assertEquals(setOf("flavour", "duplicates"), Staff090CatalogueRepository.metaOf(json, body).fieldErrors().keys)
        val c = data<MarcCancelResult>("marc_cancelled")
        assertTrue(c.cancelled)
        assertEquals("ce1b2d36-6abc-4aa9-91fc-8fea6d95e644", c.id)
    }

    // ---------- copy cataloguing ----------

    @Test fun copyCatalogingServers() {
        val s = data<CopyCatalogingServers>("cc_servers")
        assertTrue(s.servers.any { it.key == "local" && it.kind == "local" })
        assertTrue(s.servers.any { it.key == "preset:loc" && it.kind == "preset" })
        assertEquals(listOf("title", "author", "isbn", "issn", "any"), s.indexes.map { it.value })
        assertEquals(25, s.maxResultsPerServer)
        assertEquals(100, s.maxImportIds)
    }

    @Test fun copyCatalogingSearch() {
        val s = data<CopyCatalogingSearch>("cc_search")
        assertEquals("53248631-5faf-463d-8da2-09b7f326a9f3", s.searchId)
        assertEquals(2, s.results.size)
        val first = s.results.first()
        assertTrue(Regex("^[a-f0-9]{16}$").matches(first.id))
        assertEquals("Libro Cat090 Prova", first.title)
        assertTrue(first.detail.any { it.tag == "245" })
        val report = s.servers.single()
        assertEquals("local", report.key)
        assertEquals(2, report.count)
        assertNull(report.error)
    }

    @Test fun copyCatalogingImportAndReplay() {
        val r = data<CopyCatalogingImport>("cc_import")
        assertEquals(1, r.imported)
        val item = r.items.single()
        assertEquals("created", item.action)
        assertEquals(338683, item.bookId)
        val replay = json.decodeFromString<Envelope<CopyCatalogingImport>>(sample("cc_import_replayed"))
        assertEquals(true, replay.meta?.replayed)
        assertEquals(338683, replay.data!!.items.single().bookId)
        val failed = data<CopyCatalogingImport>("cc_import_failed")
        assertEquals(1, failed.failed)
        assertEquals("failed", failed.items.single().action)
        assertNull(failed.items.single().bookId)
    }

    // ---------- health ----------

    @Test fun healthTurnsTheCatalogueEntriesOn() {
        val health = json.decodeFromString<Envelope<HealthPayload>>(sample("health")).data!!
        val areas = StaffAreas.of("admin", InstanceFeatures.from(health), online = true).s090
        assertTrue(areas.suppliers && areas.marc && areas.catalogueCards && areas.rdaTypes)
        assertTrue(areas.anyCatalogue)
        assertFalse(StaffAreas.of("admin", InstanceFeatures.from(health), online = false).s090.marc)
    }

    @Test fun rawMetaToleratesOtherBodies() {
        assertNull(Staff090CatalogueRepository.metaOf(json, "<html>proxy error</html>"))
        assertTrue(Staff090CatalogueRepository.metaOf(json, """{"meta":{}}""") is JsonObject)
        assertTrue(Staff090CatalogueRepository.metaOf(json, """{"meta":{}}""").fieldErrors().isEmpty())
        assertNull(null.metaBoolean("can_create"))
    }
}
