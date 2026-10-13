package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hub → Merge duplicates: a suggested author group merged into the record with most books. */
class MergeFlowTest : AppFlowTest() {

    @Test fun mergeASuggestedDuplicateGroup() {
        launch {
            json("GET staff/authors/duplicates", "staff_author_duplicates")
            json("POST staff/authors/merge", "merge_result")
        }
        openStaffHub()
        click(text(R.string.staff_merges))
        waitText(text(R.string.staff_merge_author_books, "Woolf, Virginia", 1))
        clickTag("dup-group-0")
        clickTag("merge-confirm")
        val body = lastBody("POST", "staff/authors/merge")
        assertTrue(body, body.contains("\"ids\":[3,8]"))
        assertTrue(body, body.contains("\"primary_id\":3"))
        waitText(text(R.string.staff_merge_done, 2))
    }
}
