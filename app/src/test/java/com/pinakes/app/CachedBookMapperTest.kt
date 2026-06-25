package com.pinakes.app

import com.pinakes.app.data.local.toCached
import com.pinakes.app.data.local.toSummary
import com.pinakes.app.data.model.BookSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** Entity ↔ summary mapping that backs the offline catalog cache. */
class CachedBookMapperTest {

    private val sample = BookSummary(
        id = 7,
        title = "Orlando",
        subtitle = "A Biography",
        author = "Virginia Woolf",
        publisher = "Hogarth",
        genre = "Romanzo",
        year = 1928,
        language = "en",
        mediaType = "book",
        isbn13 = "9780156701600",
        coverUrl = "https://lib.example.org/covers/7.jpg",
        copiesTotal = 3,
        copiesAvailable = 1,
        loanableNow = true,
    )

    @Test fun roundTripPreservesAllFields() {
        assertEquals(sample, sample.toCached(0).toSummary())
    }

    @Test fun cachedKeepsPosition() {
        assertEquals(5, sample.toCached(5).position)
    }

    @Test fun cachedKeepsPrimaryKey() {
        assertEquals(7, sample.toCached(0).id)
    }
}
