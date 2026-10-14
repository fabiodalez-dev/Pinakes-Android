package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hub → Archives: publish an unpublished unit, and create a new one (validated before sending). */
class ArchivesStaffFlowTest : AppFlowTest() {

    private fun open(configure: FakePinakes.() -> Unit) {
        launch {
            json("GET staff/archives", "archives_staff")
            json("GET staff/archives/lookups", "archive_lookups")
            json("GET staff/archives/3", "archive_3")
            configure()
        }
        openStaffHub()
        click(text(R.string.staff_archives))
        waitText("Fondo Rossi")
    }

    @Test fun publishAnUnpublishedUnitKeepsEveryOtherField() {
        open { json("PUT staff/archives/3", "archive_3_published") }
        click("Fondo Rossi")
        waitText("IT-PIN-1")
        click(text(R.string.staff_archive_published))
        clickTag("archive-save")
        val body = lastBody("PUT", "staff/archives/3")
        assertTrue(body, body.contains("\"published\":true"))
        assertTrue(body, body.contains("\"reference_code\":\"IT-PIN-1\""))
        assertTrue(body, body.contains("\"date_start\":1920"))
        assertTrue(body, body.contains("\"extent\":\"3 buste\""))
        waitText(text(R.string.staff_archive_saved))
    }

    @Test fun createAUnitAfterTheRequiredFields() {
        open { json("POST staff/archives", "archive_10", status = 201) }
        clickDescription(text(R.string.staff_archive_new))
        clickTag("archive-save")
        waitText(text(R.string.staff_form_fix_errors))
        assertTrue(api.recorded("POST", "staff/archives").isEmpty())
        typeInto(text(R.string.staff_archive_reference_code), "IT-PIN-9")
        typeInto(text(R.string.staff_archive_constructed_title), "Diari")
        clickTag("archive-save")
        val body = lastBody("POST", "staff/archives")
        assertTrue(body, body.contains("\"reference_code\":\"IT-PIN-9\""))
        assertTrue(body, body.contains("\"institution_code\":\"PINAKES\""))
        assertTrue(body, body.contains("\"date_start\":null"))
        // Saved: the unit now takes authorities and files.
        waitText(text(R.string.staff_archive_created))
        waitText(text(R.string.staff_archive_attach_authority))
    }
}
