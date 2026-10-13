package com.pinakes.app

import com.pinakes.app.data.local.toCached
import com.pinakes.app.data.local.toSummary
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.data.model.SyncBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Extra round-trip/edge cases for the offline-cache entity↔summary mapping. */
class CachedBookMapperEdgeTest {

    @Test fun allOptionalNullsRoundTrip() {
        val s = BookSummary(id = 1, title = "Bare")   // every optional left null/default
        assertEquals(s, s.toCached().toSummary())
    }

    @Test fun fullyPopulatedRoundTrips() {
        val s = BookSummary(
            id = 42, title = "T", subtitle = "Sub", author = "A", publisher = "P", genre = "G",
            year = 2000, language = "it", mediaType = "audiobook", isbn13 = "9781234567897",
            coverUrl = "https://x/y.jpg", copiesTotal = 5, copiesAvailable = 2, loanableNow = false,
        )
        assertEquals(s, s.toCached().toSummary())
    }

    @Test fun availabilityFlagsSurviveTheRoundTrip() {
        val notLoanableButCopies = BookSummary(id = 1, loanableNow = false, copiesAvailable = 3)
        val back = notLoanableButCopies.toCached().toSummary()
        assertFalse(back.loanableNow)
        assertEquals(3, back.copiesAvailable)
        assertTrue(back.available)   // derived: copies > 0
    }

    @Test fun loanableNowTrueSurvives() {
        val loanable = BookSummary(id = 2, loanableNow = true, copiesAvailable = 0)
        val back = loanable.toCached().toSummary()
        assertTrue(back.loanableNow)
        assertTrue(back.available)
    }

    @Test fun syncRowWithFreeCopiesIsLoanable() {
        // The sync payload may omit loanable_now; free copies still make it loanable.
        val c = SyncBook(id = 3, title = "X", copiesAvailable = 1, loanableNow = false).toCached()
        assertTrue(c.loanableNow)
    }
}
