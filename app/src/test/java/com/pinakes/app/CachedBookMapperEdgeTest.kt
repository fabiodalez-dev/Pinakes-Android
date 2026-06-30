package com.pinakes.app

import com.pinakes.app.data.local.toCached
import com.pinakes.app.data.local.toSummary
import com.pinakes.app.data.model.BookSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Extra round-trip/edge cases for the offline-cache entity↔summary mapping (touched: #9). */
class CachedBookMapperEdgeTest {

    @Test fun allOptionalNullsRoundTrip() {
        val s = BookSummary(id = 1, title = "Bare")   // every optional left null/default
        assertEquals(s, s.toCached(0).toSummary())
    }

    @Test fun fullyPopulatedRoundTrips() {
        val s = BookSummary(
            id = 42, title = "T", subtitle = "Sub", author = "A", publisher = "P", genre = "G",
            year = 2000, language = "it", mediaType = "audiobook", isbn13 = "9781234567897",
            coverUrl = "https://x/y.jpg", copiesTotal = 5, copiesAvailable = 2, loanableNow = false,
        )
        assertEquals(s, s.toCached(3).toSummary())
    }

    @Test fun positionIsCarriedAndIndependentOfId() {
        val s = BookSummary(id = 7, title = "X")
        assertEquals(0, s.toCached(0).position)
        assertEquals(999, s.toCached(999).position)
        assertEquals(7, s.toCached(0).id)   // id stays the book id, not the position
    }

    @Test fun availabilityFlagsSurviveTheRoundTrip() {
        val notLoanableButCopies = BookSummary(id = 1, loanableNow = false, copiesAvailable = 3)
        val back = notLoanableButCopies.toCached(0).toSummary()
        assertFalse(back.loanableNow)
        assertEquals(3, back.copiesAvailable)
        assertTrue(back.available)   // derived: copies > 0
    }

    @Test fun loanableNowTrueSurvives() {
        val loanable = BookSummary(id = 2, loanableNow = true, copiesAvailable = 0)
        val back = loanable.toCached(1).toSummary()
        assertTrue(back.loanableNow)
        assertTrue(back.available)
    }
}
