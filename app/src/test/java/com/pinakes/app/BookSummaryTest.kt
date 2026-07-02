package com.pinakes.app

import com.pinakes.app.data.model.BookSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Derived availability used by the catalog/home shelves (touched area: offline catalog). */
class BookSummaryTest {

    @Test fun availableWhenLoanableNow() {
        assertTrue(BookSummary(id = 1, loanableNow = true, copiesAvailable = 0).available)
    }

    @Test fun availableWhenCopiesFree() {
        assertTrue(BookSummary(id = 1, loanableNow = false, copiesAvailable = 2).available)
    }

    @Test fun notAvailableWhenNothingFree() {
        assertFalse(BookSummary(id = 1, loanableNow = false, copiesAvailable = 0).available)
    }

    @Test fun authorsLabelIsEmptyWhenNull() {
        assertEquals("", BookSummary(id = 1, author = null).authorsLabel)
    }

    @Test fun authorsLabelEchoesAuthor() {
        assertEquals("Virginia Woolf", BookSummary(id = 1, author = "Virginia Woolf").authorsLabel)
    }
}
