package com.pinakes.app.staffe2e

import androidx.compose.ui.test.hasTestTag
import com.pinakes.app.R
import org.junit.Test

/** Staff → Authors → an author: padded info card (count, dates) and the author's books. */
class StaffRecordDetailFlowTest : AppFlowTest() {

    @Test fun authorDetailShowsTheInfoCardAndTheBooks() {
        launch {
            json("GET staff/authors", "staff_authors")
            json("GET staff/authors/3", "staff_author_3")
        }
        openTab(R.string.nav_profile)
        clickUntil(text(R.string.profile_action_staff), text(R.string.staff_scan_subtitle))
        click(text(R.string.staff_authors))
        click("Virginia Woolf")
        waitFor(hasTestTag("record-info-card"))
        waitText(text(R.string.staff_record_books_count))
        waitText(text(R.string.staff_record_created))
        waitText("118635506")
        // The book list (GET /staff/books?author_id=3) uses the whole-cover cards.
        waitText("Orlando")
        waitRequest("GET", "staff/books")
        val query = api.recorded("GET", "staff/books").last().requestUrl?.queryParameter("author_id")
        org.junit.Assert.assertEquals("3", query)
    }
}
