package com.pinakes.app.staffe2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import com.pinakes.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ISBN scanner, driven through the always-visible manual entry (as on an emulator). */
class ScannerFlowTest : AppFlowTest() {

    private fun openScanner() {
        openTab(R.string.nav_catalog)
        clickDescription(text(R.string.staff_scan))
        denyCameraPromptIfShown()
        waitText(text(R.string.staff_scan_manual_label))
    }

    private fun typeCode(code: String) {
        compose.onAllNodes(hasSetTextAction() and hasText(text(R.string.staff_scan_manual_label))).onFirst().performTextInput(code)
        clickDescription(text(R.string.staff_scan_lookup))
    }

    @Test fun cameraDeniedKeepsManualEntryAndExplainsWhy() {
        launch()
        openScanner()
        // Denied (or never granted): the rationale or settings fallback replaces the preview,
        // and the manual field stays usable.
        assertTrue(
            exists(text(R.string.staff_scan_permission_rationale)) || exists(text(R.string.staff_scan_permission_denied)) ||
                exists(text(R.string.staff_camera_unavailable))
        )
        waitText(text(R.string.staff_scan_manual_label))
    }

    @Test fun manualIsbnShowsThePreviewAndAddsTheTitle() {
        launch { json("GET staff/isbn/9788845292613", "isbn_found"); json("POST staff/books", "staff_book_created", 201) }
        openScanner()
        typeCode("978-88-452-9261-3")
        waitText("Il nome della rosa")
        click(text(R.string.staff_scan_confirm))
        waitText(text(R.string.staff_scan_session_count, 1))
        val body = api.recorded("POST", "staff/books").single().body.readUtf8()
        assertTrue(body, body.contains("\"isbn13\":\"9788845292613\""))
        assertTrue(body, body.contains("\"copies\":1"))
        assertTrue(body, body.contains("\"authors_new\":[\"Umberto Eco\"]"))
    }

    @Test fun duplicateOffersToAddCopies() {
        launch { json("GET staff/isbn/9788845292613", "isbn_duplicate"); json("POST staff/books/42/copies", "copies_added", 201) }
        openScanner()
        typeCode("9788845292613")
        waitText(text(R.string.staff_scan_duplicate))
        click(text(R.string.staff_scan_add_copy))
        waitText(text(R.string.staff_scan_added_copies, 1))
        assertEquals(1, api.recorded("POST", "staff/books/42/copies").size)
        assertTrue(api.recorded("POST", "staff/books").isEmpty())
    }

    @Test fun notFoundLeadsToTheManualFormWithTheCode() {
        launch { json("GET staff/isbn/4006381333931", "isbn_not_found") }
        openScanner()
        typeCode("4006381333931")
        waitText(text(R.string.staff_scan_not_found))
        click(text(R.string.staff_scan_create_manual))
        waitText(text(R.string.staff_book_new))
        waitText("4006381333931")
        assertTrue(api.recorded("POST", "staff/books").isEmpty())
    }

    @Test fun invalidCodeIsRejectedWithoutALookup() {
        launch()
        openScanner()
        typeCode("9788845292614")
        waitText(text(R.string.staff_identifier_invalid))
        assertTrue(api.requests.none { it.requestUrl?.encodedPath?.contains("staff/isbn") == true })
        assertTrue(compose.onAllNodes(hasContentDescription(text(R.string.staff_scan_lookup))).fetchSemanticsNodes().isNotEmpty())
    }
}
