package com.pinakes.app.staffe2e

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.pinakes.app.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The manual record form: required title, complete locations, true patches on edit. */
class BookFormFlowTest : AppFlowTest() {

    private fun openHub() {
        openTab(R.string.nav_profile)
        clickUntil(text(R.string.profile_action_staff), text(R.string.staff_scan_subtitle))
    }

    private fun field(label: String) = compose.onAllNodes(hasSetTextAction() and hasText(label, substring = true)).onFirst()

    @Test fun titleIsRequiredAndNothingIsSent() {
        launch()
        openHub()
        click(text(R.string.staff_add_manual))
        waitText(text(R.string.staff_book_save))
        click(text(R.string.staff_book_save))
        waitText(text(R.string.staff_field_required))
        assertTrue(api.recorded("POST", "staff/books").isEmpty())
    }

    @Test fun shelfWithoutLevelShowsTheErrorAndSendsNothing() {
        launch()
        openHub()
        click(text(R.string.staff_add_manual))
        waitText(text(R.string.staff_field_title))
        field(text(R.string.staff_field_title)).performTextInput("Spartito senza ISBN")
        click(text(R.string.staff_shelf))
        click("A · Narrativa")
        click(text(R.string.staff_book_save))
        waitText(text(R.string.staff_level_required))
        assertTrue(api.recorded("POST", "staff/books").isEmpty())
    }

    @Test fun editingOneFieldSendsOnlyThatKey() {
        launch { json("PUT staff/books/5", "staff_book_5") }
        openHub()
        click(text(R.string.staff_books))
        click("Orlando")
        waitText("A Biography")
        compose.onAllNodes(hasSetTextAction() and hasText("A Biography")).onFirst().performTextReplacement("A new subtitle")
        click(text(R.string.staff_book_save))
        waitRequest("PUT", "staff/books/5")
        val body = Json.parseToJsonElement(api.recorded("PUT", "staff/books/5").single().body.readUtf8()).jsonObject
        assertEquals(setOf("subtitle"), body.keys)
        assertEquals("\"A new subtitle\"", body["subtitle"].toString())
    }
}
