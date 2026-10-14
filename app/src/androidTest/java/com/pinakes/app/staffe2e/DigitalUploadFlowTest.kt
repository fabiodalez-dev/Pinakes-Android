package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Book editor → Digital content: an eBook picked in the system picker is streamed to the server. */
class DigitalUploadFlowTest : AppFlowTest() {

    @Test fun uploadAnEbookFromTheBookEditor() {
        val name = "e2e-ebook-${System.currentTimeMillis() % 100000}.pdf"
        val uri = publishDownload(name, "%PDF-1.4\n% e2e\n", "application/pdf")
        try {
            launch(health = "health_staff_digital") { json("POST staff/books/5/digital", "digital_uploaded") }
            openStaffHub()
            click(text(R.string.staff_books))
            click("Orlando")
            click(text(R.string.staff_digital_ebook))
            pickInDocumentsUi(name)
            waitRequest("POST", "staff/books/5/digital", timeoutMs = 20_000)
            val body = api.recorded("POST", "staff/books/5/digital").last().body.clone().readUtf8()
            assertTrue(body, body.contains("filename=\"$name\""))
            assertTrue(body, body.contains("name=\"digital_type\"") && body.contains("ebook"))
            assertTrue(body, body.contains("%PDF-1.4"))
            waitText(text(R.string.staff_digital_ebook_done))
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test fun withoutTheFlagThereIsNoDigitalSection() {
        launch()
        openStaffHub()
        click(text(R.string.staff_books))
        click("Orlando")
        waitText(text(R.string.staff_book_save))
        assertFalse(exists(text(R.string.staff_digital_ebook)))
    }
}
