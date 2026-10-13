package com.pinakes.app.staffe2e

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextReplacement
import com.pinakes.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hub → Series: the tree, a series' books, a number, and a rename that clashes and merges. */
class SeriesFlowTest : AppFlowTest() {

    private fun openRincewind(configure: FakePinakes.() -> Unit = {}) {
        launch {
            json("GET staff/series", "staff_series")
            json("GET staff/series/5", "staff_series_5")
            configure()
        }
        openStaffHub()
        click(text(R.string.staff_series))
        waitText("Discworld")
        assertTrue(!exists("Rincewind"))
        clickDescription(text(R.string.staff_expand))
        click("Rincewind")
        waitText("Sourcery")
    }

    @Test fun numberABookInTheSeries() {
        openRincewind { json("PUT staff/series/5/books/7", "staff_series_5_numbered") }
        clickTag("series-book-7")
        typeInto(text(R.string.staff_field_series_number), "2")
        clickInDialog(text(R.string.action_save))
        assertEquals("""{"series_number":"2"}""", lastBody("PUT", "staff/series/5/books/7"))
        waitText(text(R.string.staff_series_number_saved))
    }

    @Test fun renameOntoAnExistingNameAsksAndMerges() {
        openRincewind {
            body("POST staff/series/5/rename", 409, """{"data":null,"meta":{"existing":{"id":6,"name":"Gialli Mondadori"}},"error":{"code":"duplicate","message":"x"}}""")
        }
        clickDescription(text(R.string.staff_more_actions))
        click(text(R.string.staff_series_rename))
        compose.onAllNodes(hasSetTextAction() and hasTestTag("text-input")).onFirst().performTextReplacement("Gialli Mondadori")
        clickInDialog(text(R.string.staff_series_rename))
        waitText(text(R.string.staff_series_rename_clash, "Gialli Mondadori"))
        assertTrue(lastBody("POST", "staff/series/5/rename").contains("\"merge_if_exists\":false"))
        api.json("POST staff/series/5/rename", "staff_series_5")
        clickInDialog(text(R.string.staff_series_merge_into, "Gialli Mondadori"))
        compose.waitUntil(10_000) { api.recorded("POST", "staff/series/5/rename").size == 2 }
        assertTrue(api.recorded("POST", "staff/series/5/rename").last().body.clone().readUtf8().contains("\"merge_if_exists\":true"))
    }
}
