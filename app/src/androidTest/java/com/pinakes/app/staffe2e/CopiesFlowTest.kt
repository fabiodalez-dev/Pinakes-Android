package com.pinakes.app.staffe2e

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextReplacement
import com.pinakes.app.R
import org.junit.Assert.assertTrue
import org.junit.Test

/** Book editor → copies: edit a copy's state and notes, a refused delete, a copy with its own code. */
class CopiesFlowTest : AppFlowTest() {

    private fun openBook(configure: FakePinakes.() -> Unit = {}) {
        launch {
            json("GET staff/books/5", "staff_book_5_copies")
            configure()
        }
        openStaffHub()
        click(text(R.string.staff_books))
        click("Orlando")
        waitText("INV-0011")
    }

    @Test fun editACopyStateAndNotes() {
        openBook { json("PUT staff/copies/11", "staff_copy_11") }
        click("INV-0011")
        click(text(R.string.staff_copy_status))
        click("In manutenzione")
        compose.onAllNodes(hasSetTextAction() and androidx.compose.ui.test.hasTestTag("copy-notes")).onFirst().performTextReplacement("Cover loose")
        clickTag("copy-save")
        val body = lastBody("PUT", "staff/copies/11")
        assertTrue(body, body.contains("\"status\":\"manutenzione\""))
        assertTrue(body, body.contains("\"notes\":\"Cover loose\""))
        waitText(text(R.string.staff_copy_saved))
    }

    @Test fun aCopyWithHistoryCannotBeDeleted() {
        openBook {
            body("DELETE staff/copies/12", 409, """{"data":null,"meta":{"reason":"copy_has_history"},"error":{"code":"copy_has_history","message":"x"}}""")
        }
        click("INV-0012")
        click(text(R.string.staff_copy_delete))
        clickInDialog(text(R.string.staff_copy_delete))
        waitRequest("DELETE", "staff/copies/12")
        waitText(text(R.string.staff_err_copy_has_history))
    }

    @Test fun addACopyWithAnExplicitCode() {
        openBook { json("POST staff/books/5/copies", "copies_added_code", status = 201) }
        click(text(R.string.staff_copy_add_with_code))
        typeInto(text(R.string.staff_copy_code), "LIB-7")
        clickTag("copy-save")
        val body = lastBody("POST", "staff/books/5/copies")
        assertTrue(body, body.contains("\"quantity\":1"))
        assertTrue(body, body.contains("\"inventory_number\":\"LIB-7\""))
        waitText(text(R.string.staff_copy_added))
    }

    @Test fun aTakenCodeIsReported() {
        openBook {
            body("POST staff/books/5/copies", 409, """{"data":null,"meta":{"existing":{"id":9,"inventory_number":"LIB-7","book_id":2}},"error":{"code":"duplicate","message":"x"}}""")
        }
        click(text(R.string.staff_copy_add_with_code))
        typeInto(text(R.string.staff_copy_code), "LIB-7")
        clickTag("copy-save")
        waitText(text(R.string.staff_copy_code_taken, "LIB-7"))
    }
}
