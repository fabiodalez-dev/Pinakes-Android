package com.pinakes.app.staffe2e

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.pinakes.app.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pinakes 0.9.0 cataloguing from the staff hub: suppliers, the book form's supplier and RDA
 * fields, MARC import and export, copy cataloguing. The fixtures `staff090_catalogue_*` are
 * bodies the slot server answered (Mobile API 1.10.0).
 */
class Staff090CatalogueFlowTest : AppFlowTest() {

    private val health = "staff090_catalogue_health"

    private fun openCataloguing(entry: Int, configure: FakePinakes.() -> Unit) {
        launch(health = health) {
            json("GET staff/lookups", "staff090_catalogue_lookups_full")
            json("GET staff/marc/options", "staff090_catalogue_marc_options")
            configure()
        }
        openStaffHub()
        click(text(entry))
    }

    /** Scroll the lazy list until the node tagged [tag] is composed. */
    private fun scrollToTag(tag: String) {
        val lists = compose.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())
        compose.waitUntil(10_000) { lists.fetchSemanticsNodes().isNotEmpty() }
        for (i in 0 until lists.fetchSemanticsNodes().size) {
            if (runCatching { lists[i].performScrollToNode(androidx.compose.ui.test.hasTestTag(tag)) }.isSuccess) return
        }
    }

    // ---------- suppliers ----------

    @Test fun aSupplierFieldErrorIsShownThenTheRetryKeepsItsKey() {
        openCataloguing(R.string.staff090_suppliers) {
            json("GET staff/suppliers", "staff090_catalogue_suppliers")
            sequence("POST staff/suppliers", 422 to "staff090_catalogue_supplier_422", 201 to "staff090_catalogue_supplier_created")
        }
        waitText("Libreria Cat090 Prova")
        clickDescription(text(R.string.staff090_catalogue_supplier_new))
        typeIntoTag("supplier-field-name", "Libreria Nuova")
        clickTag("supplier-save")
        // The server's own text, under its field.
        waitText("Il nome del fornitore è obbligatorio.")
        // The field error moved the button: tap again until the retry reaches the server.
        repeat(3) { if (api.recorded("POST", "staff/suppliers").size < 2) runCatching { clickTag("supplier-save"); compose.waitUntil(3_000) { api.recorded("POST", "staff/suppliers").size >= 2 } } }
        waitGone(androidx.compose.ui.test.hasTestTag("supplier-save"))
        val posts = api.recorded("POST", "staff/suppliers")
        assertEquals(2, posts.size)
        val key = posts.first().getHeader("Idempotency-Key")
        assertTrue(!key.isNullOrBlank())
        assertEquals(key, posts.last().getHeader("Idempotency-Key"))
        assertEquals("""{"name":"Libreria Nuova"}""", posts.last().body.clone().readUtf8())
    }

    @Test fun deletingASupplierSaysHowManyBooksLoseIt() {
        openCataloguing(R.string.staff090_suppliers) {
            body(
                "GET staff/suppliers", 200,
                FakePinakes.fixture("staff090_catalogue_suppliers").replace("\"book_count\":0", "\"book_count\":2"),
            )
            json("DELETE staff/suppliers/21", "staff090_catalogue_supplier_deleted")
        }
        waitText("Libreria Cat090 Prova")
        clickDescription(text(R.string.cd_more_options))
        click(text(R.string.staff090_catalogue_supplier_delete))
        waitText(text(R.string.staff090_catalogue_supplier_delete_books, "Libreria Cat090 Prova", 2))
        clickInDialog(text(R.string.staff090_catalogue_supplier_delete))
        waitText(text(R.string.staff090_catalogue_supplier_deleted, 2))
        assertEquals(1, api.recorded("DELETE", "staff/suppliers/21").size)
    }

    // ---------- book form ----------

    @Test fun aNewRecordCarriesSupplierAndRdaOverride() {
        launch(health = health) {
            json("GET staff/lookups", "staff090_catalogue_lookups_full")
            json("POST staff/books", "staff_book_created", status = 201)
        }
        openStaffHub()
        click(text(R.string.staff_add_manual))
        waitText(text(R.string.staff_field_title))
        compose.onAllNodes(hasSetTextAction() and hasText(text(R.string.staff_field_title), substring = true)).onFirst().performTextInput("Libro con fornitore")
        clickTag("book-supplier")
        click("Libreria Cat090 Prova")
        clickTag("book-rda-content")
        click("Parola parlata")
        click(text(R.string.staff_book_save))
        waitRequest("POST", "staff/books")
        val body = Json.parseToJsonElement(api.recorded("POST", "staff/books").single().body.readUtf8()).jsonObject
        assertEquals("21", body["supplier_id"].toString())
        assertEquals("\"spw\"", body["rda_content_type"].toString())
        assertFalse(body.containsKey("rda_media_type"))
    }

    @Test fun withTheModulesOffTheFormSendsNoCataloguingKey() {
        launch { json("POST staff/books", "staff_book_created", status = 201) }
        openStaffHub()
        click(text(R.string.staff_add_manual))
        waitText(text(R.string.staff_field_title))
        compose.onAllNodes(hasSetTextAction() and hasText(text(R.string.staff_field_title), substring = true)).onFirst().performTextInput("Libro semplice")
        assertFalse(exists(text(R.string.staff090_catalogue_rda_title)))
        click(text(R.string.staff_book_save))
        waitRequest("POST", "staff/books")
        val body = Json.parseToJsonElement(api.recorded("POST", "staff/books").single().body.readUtf8()).jsonObject
        assertFalse(body.containsKey("supplier_id"))
        assertFalse(body.containsKey("rda_content_type"))
    }

    // ---------- MARC ----------

    @Test fun uploadPreviewThenChunksFromTheServersPosition() {
        val name = "e2e-marc-${System.currentTimeMillis() % 100000}.mrc"
        val bytes = instrumentation.context.assets.open("fixtures/staff090_catalogue_prova.mrc").use { it.readBytes() }
        // The record is plain ASCII (control characters included), so the text round trip keeps every byte.
        val uri = publishDownload(name, String(bytes, Charsets.US_ASCII), mime = "application/octet-stream")
        try {
            openCataloguing(R.string.staff090_marc) {
                json("POST staff/marc/imports", "staff090_catalogue_marc_job_ready", status = 201)
                json("GET staff/marc/imports/6dd79110-5736-4456-aabb-07e682b77466", "staff090_catalogue_marc_job_ready")
                // The server says it is still at 0 (another device's chunk failed), then completes.
                sequence(
                    "POST staff/marc/imports/6dd79110-5736-4456-aabb-07e682b77466/chunk",
                    409 to "staff090_catalogue_marc_chunk_409",
                    200 to "staff090_catalogue_marc_job_completed",
                )
            }
            click(text(R.string.staff_import_choose_file))
            pickInDocumentsUi(name)
            waitText(name, timeoutMs = 15_000)
            clickTag("marc-supplier")
            click("Libreria Cat090 Prova")
            clickTag("marc-upload")
            waitText(text(R.string.staff090_catalogue_marc_preview))
            val upload = api.recorded("POST", "staff/marc/imports").single().body.clone().readUtf8()
            assertTrue(upload, upload.contains("filename=\"$name\""))
            assertTrue(upload, upload.contains("name=\"supplier_id\"") && upload.contains("21"))
            assertTrue(upload, upload.contains("name=\"duplicates\""))
            waitText("Libro Cat090 Prova")
            clickTag("marc-run")
            waitText(text(R.string.staff_import_status_done), timeoutMs = 20_000)
            val starts = api.recorded("POST", "staff/marc/imports/6dd79110-5736-4456-aabb-07e682b77466/chunk").map { it.body.clone().readUtf8() }
            assertEquals(listOf("""{"start":0}""", """{"start":0}"""), starts)
            waitText(text(R.string.staff090_catalogue_marc_progress, 1, 1))
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private val otherJob = "3232bb79-91dd-427d-afae-ce574c49af04"

    @Test fun aJobStartedElsewhereIsResumedFromTheList() {
        openCataloguing(R.string.staff090_marc) {
            json("GET staff/marc/imports", "staff090_catalogue_marc_jobs")
            body(
                "POST staff/marc/imports/$otherJob/chunk", 200,
                FakePinakes.fixture("staff090_catalogue_marc_job_completed").replace("6dd79110-5736-4456-aabb-07e682b77466", otherJob),
            )
        }
        waitText("altro-dispositivo.mrc")
        // The cancelled job of the same list is not offered.
        assertFalse(exists("vecchio.mrc"))
        clickTag("marc-open-resume-$otherJob")
        waitText(text(R.string.staff_import_status_done), timeoutMs = 20_000)
        assertEquals(listOf("""{"start":0}"""), api.recorded("POST", "staff/marc/imports/$otherJob/chunk").map { it.body.clone().readUtf8() })
    }

    @Test fun aListedJobCanBeCancelled() {
        openCataloguing(R.string.staff090_marc) {
            json("GET staff/marc/imports", "staff090_catalogue_marc_jobs")
            body("DELETE staff/marc/imports/$otherJob", 200, """{"data":{"id":"$otherJob","cancelled":true},"meta":{},"error":null}""")
        }
        waitText("altro-dispositivo.mrc")
        clickTag("marc-open-cancel-$otherJob")
        waitRequest("DELETE", "staff/marc/imports/$otherJob")
        waitGone(hasText("altro-dispositivo.mrc"))
    }

    @Test fun exportTheWholeCatalogue() {
        openCataloguing(R.string.staff090_marc) {
            body(
                "GET staff/marc/export", 200, String(instrumentation.context.assets.open("fixtures/staff090_catalogue_prova.mrc").use { it.readBytes() }, Charsets.ISO_8859_1),
                headers = mapOf("Content-Type" to "application/marc", "Content-Disposition" to "attachment; filename=\"catalogo_unimarc_2026-11-01.mrc\""),
            )
        }
        clickTag("marc-tab-export")
        click("UNIMARC (.mrc)")
        clickTag("marc-export")
        waitText("catalogo_unimarc_2026-11-01.mrc")
        val request = api.recorded("GET", "staff/marc/export").single()
        assertEquals("unimarc", request.requestUrl?.queryParameter("format"))
        assertEquals(null, request.requestUrl?.queryParameter("ids"))
    }

    // ---------- copy cataloguing ----------

    @Test fun searchImportAndOpenTheNewBook() {
        openCataloguing(R.string.staff090_copy_cataloging) {
            json("GET staff/copy-cataloging/servers", "staff090_catalogue_cc_servers")
            json("POST staff/copy-cataloging/search", "staff090_catalogue_cc_search", status = 201)
            json("POST staff/copy-cataloging/import", "staff090_catalogue_cc_import")
            json("GET staff/books/338683", "staff_book_5")
        }
        clickTag("copy-server-local")
        typeIntoTag("copy-term", "Libro Cat090 Prova")
        clickTag("copy-search")
        waitRequest("POST", "staff/copy-cataloging/search")
        val search = Json.parseToJsonElement(api.recorded("POST", "staff/copy-cataloging/search").single().body.readUtf8()).jsonObject
        assertEquals("""["local"]""", search["servers"].toString())
        assertEquals("\"Libro Cat090 Prova\"", search["term"].toString())
        // The results sit below the search form: scroll the list to them first.
        scrollToTag("copy-result-6eb1848e432e386c")
        clickTag("copy-result-6eb1848e432e386c")
        scrollToTag("copy-import")
        clickTag("copy-import")
        waitRequest("POST", "staff/copy-cataloging/import")
        val import = api.recorded("POST", "staff/copy-cataloging/import").single()
        assertTrue(!import.getHeader("Idempotency-Key").isNullOrBlank())
        val body = Json.parseToJsonElement(import.body.readUtf8()).jsonObject
        assertEquals("""["6eb1848e432e386c"]""", body["ids"].toString())
        assertEquals("\"53248631-5faf-463d-8da2-09b7f326a9f3\"", body["search_id"].toString())
        scrollListTo(text(R.string.staff090_catalogue_copy_action_created))
        click(text(R.string.staff090_catalogue_copy_action_created))
        waitRequest("GET", "staff/books/338683")
    }

    @Test fun withoutTheZ3950PluginTheReasonIsShown() {
        openCataloguing(R.string.staff090_copy_cataloging) {
            json("GET staff/marc/options", "staff090_catalogue_marc_options_copy_unavailable")
        }
        waitText(text(R.string.staff090_catalogue_copy_reason_plugin))
        assertTrue(api.recorded("GET", "staff/copy-cataloging/servers").isEmpty())
    }
}
