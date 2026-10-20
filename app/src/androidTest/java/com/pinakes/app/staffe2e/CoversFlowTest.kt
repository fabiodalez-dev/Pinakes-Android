package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers & enrichment: the per-book loop over the missing covers, and the automatic enrichment switch. */
class CoversFlowTest : AppFlowTest() {

    @Test fun fetchMissingCoversOneByOneAndTurnOnEnrichment() {
        launch {
            json("GET staff/covers/missing", "missing_covers")
            json("GET staff/enrich", "enrich_overview")
            json("POST staff/books/5/cover/fetch", "cover_fetched")
            json("POST staff/books/6/cover/fetch", "cover_fetched")
            json("PUT staff/enrich/cron", "enrich_cron_on")
        }
        openStaffHub()
        click(text(R.string.staff_covers))
        waitText(text(R.string.staff_covers_missing_count, 2))
        clickTag("covers-loop")
        waitText(text(R.string.staff_covers_loop_progress, 2, 2, 2, 0), timeoutMs = 15_000)
        assertEquals(1, api.recorded("POST", "staff/books/5/cover/fetch").size)
        assertEquals(1, api.recorded("POST", "staff/books/6/cover/fetch").size)
        // Never the long server-side jobs without a confirmation.
        assertTrue(api.recorded("POST", "staff/covers/sync").isEmpty())
        click(text(R.string.staff_enrich_cron))
        assertEquals("""{"enabled":true}""", lastBody("PUT", "staff/enrich/cron"))
    }
}
