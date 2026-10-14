package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Imports: chunks requested in order from the server's progress, and an upload through the system picker. */
class ImportFlowTest : AppFlowTest() {

    private val jobRoutes: FakePinakes.() -> Unit = {
        json("GET staff/imports", "imports_overview")
        json("GET staff/imports/6f1c2a", "import_job_created")
        // 0 → 10, then the server says it is already at 20 (another device moved on), then 20 → 30.
        sequence(
            "POST staff/imports/6f1c2a/chunk",
            200 to "import_job_chunk_10",
            409 to "import_out_of_order_20",
            200 to "import_job_chunk_30",
        )
    }

    private fun chunkStarts(): List<String> = api.recorded("POST", "staff/imports/6f1c2a/chunk").map { it.body.clone().readUtf8() }

    @Test fun anOpenJobResumesAndFollowsTheServersProgress() {
        launch(configure = jobRoutes)
        openStaffHub()
        click(text(R.string.staff_imports))
        scrollListTo("libri.csv")
        click("libri.csv")
        waitText(text(R.string.staff_import_status_done), timeoutMs = 20_000)
        assertEquals(listOf("""{"start":0}""", """{"start":10}""", """{"start":20}"""), chunkStarts())
        waitText(text(R.string.staff_import_progress, 30, 30))
        waitText("Row 12: missing title")
        waitText("Row 25: bad ISBN")
    }

    @Test fun uploadAPickedCsvThenRunTheChunks() {
        val name = "e2e-import-${System.currentTimeMillis() % 100000}.csv"
        val uri = publishDownload(name, "titolo;autore\nOrlando;Virginia Woolf\nIl barone rampante;Italo Calvino\n")
        try {
            launch {
                jobRoutes()
                json("POST staff/imports", "import_job_created", status = 201)
            }
            openStaffHub()
            click(text(R.string.staff_imports))
            click(text(R.string.staff_import_choose_file))
            pickInDocumentsUi(name)
            waitText(name, timeoutMs = 15_000)
            clickTag("import-start")
            waitRequest("POST", "staff/imports")
            val body = api.recorded("POST", "staff/imports").last().body.clone().readUtf8()
            assertTrue(body, body.contains("filename=\"$name\""))
            assertTrue(body, body.contains("Il barone rampante;Italo Calvino"))
            assertTrue(body, body.contains("name=\"kind\"") && body.contains("csv"))
            assertTrue(body, body.contains("name=\"enable_scraping\""))
            waitText(text(R.string.staff_import_status_done), timeoutMs = 20_000)
            assertEquals(3, chunkStarts().size)
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }
}
