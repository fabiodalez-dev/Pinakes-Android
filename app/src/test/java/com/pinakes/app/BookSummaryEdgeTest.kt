package com.pinakes.app

import com.pinakes.app.data.model.BookSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Boundary cases for the derived availability/labels used by catalog & home shelves. */
class BookSummaryEdgeTest {

    @Test fun availableWhenBothLoanableAndCopies() {
        assertTrue(BookSummary(id = 1, loanableNow = true, copiesAvailable = 2).available)
    }

    @Test fun availableAtExactlyOneCopyBoundary() {
        assertTrue(BookSummary(id = 1, loanableNow = false, copiesAvailable = 1).available)
        assertFalse(BookSummary(id = 1, loanableNow = false, copiesAvailable = 0).available)
    }

    @Test fun negativeCopiesAreNotAvailableWhenNotLoanable() {
        // Defensive: a malformed negative count must not read as available.
        assertFalse(BookSummary(id = 1, loanableNow = false, copiesAvailable = -1).available)
    }

    @Test fun authorsLabelEchoesMultiWordAuthor() {
        assertEquals("Italo Calvino", BookSummary(id = 1, author = "Italo Calvino").authorsLabel)
        assertEquals("", BookSummary(id = 1, author = null).authorsLabel)
    }
}
