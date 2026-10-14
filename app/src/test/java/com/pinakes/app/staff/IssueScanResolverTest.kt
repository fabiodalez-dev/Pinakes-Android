package com.pinakes.app.staff

import com.pinakes.app.data.staff.IssueScanResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueScanResolverTest {

    private val masthead = "9771234567003"
    private val issue = "977123456700305"

    @Test fun twoBareReadsWaitForTheAddOnInsteadOfLookingUpTheMasthead() {
        val r = IssueScanResolver()
        assertNull(r.offer(masthead, 0))
        assertNull(r.offer(masthead, 40))
        assertTrue(r.holding)
        // The frame that resolves the add-on arrives within the hold: the issue wins at once.
        assertEquals(issue, r.offer(issue, 120))
        assertFalse(r.holding)
        assertNull(r.flush(2_000))
    }

    @Test fun aBareReadAfterAnAddOnReadCountsAsTheIssue() {
        val r = IssueScanResolver()
        assertNull(r.offer(issue, 0))
        // Blurred frame: only the 13 digits, but it confirms the issue read just seen.
        assertEquals(issue, r.offer(masthead, 40))
    }

    @Test fun aMastheadWithNoAddOnIsStillLookedUpAfterTheHold() {
        val r = IssueScanResolver(holdMs = 800)
        assertNull(r.offer(masthead, 0))
        assertNull(r.offer(masthead, 40))
        assertNull(r.flush(500))
        assertEquals(masthead, r.flush(840))
        assertFalse(r.holding)
    }

    @Test fun aBookEanIsNotHeld() {
        val r = IssueScanResolver()
        val isbn = "9788804668237"
        assertNull(r.offer(isbn, 0))
        assertEquals(isbn, r.offer(isbn, 40))
        assertFalse(r.holding)
    }

    @Test fun nothingIsAcceptedWhilePaused() {
        val r = IssueScanResolver()
        r.debouncer.pause()
        assertNull(r.offer(issue, 0))
        assertNull(r.offer(issue, 40))
    }
}
