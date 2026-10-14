package com.pinakes.app

import com.pinakes.app.data.local.toCached
import com.pinakes.app.data.local.toOfflineDetail
import com.pinakes.app.data.local.toSummary
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.data.model.SyncBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Entity ↔ model mapping behind the offline catalogue (Room schema v2). */
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

    private val syncBook = SyncBook(
        id = 9, title = "Le onde", author = "Virginia Woolf", publisher = "Einaudi", genre = "Romanzo",
        year = 1931, language = "Italiano", mediaType = "libro", isbn13 = "9788806219130", isbn10 = "8806219138",
        ean = null, pages = 260, description = "Sei voci.", locationLabel = "A.2.14",
        coverUrl = "https://lib.example.org/c/9.jpg", copiesTotal = 2, copiesAvailable = 0,
        loanableNow = false, isWanted = true, updatedAt = "2026-10-01 10:00:00",
    )

    @Test fun summaryRoundTripPreservesAllFields() {
        assertEquals(sample, sample.toCached().toSummary())
    }

    @Test fun cachedKeepsPrimaryKey() {
        assertEquals(7, sample.toCached().id)
    }

    @Test fun summaryRowsHaveNoChangeTimestamp() {
        assertEquals("", sample.toCached().updatedAt)
    }

    @Test fun syncBookKeepsOfflineDetailFields() {
        val c = syncBook.toCached()
        assertEquals("8806219138", c.isbn10)
        assertEquals(260, c.pages)
        assertEquals("Sei voci.", c.description)
        assertEquals("A.2.14", c.locationLabel)
        assertTrue(c.isWanted)
        assertEquals("2026-10-01 10:00:00", c.updatedAt)
    }

    @Test fun syncBookWithoutTimestampStoresEmpty() {
        assertEquals("", syncBook.copy(updatedAt = null).toCached().updatedAt)
    }

    @Test fun offlineDetailCarriesSummaryLevelData() {
        val d = syncBook.toCached().toOfflineDetail()
        assertEquals(9, d.id)
        assertEquals("Le onde", d.title)
        assertEquals("Virginia Woolf", d.authorsLabel)
        assertEquals("Einaudi", d.publisher)
        assertEquals("Romanzo", d.genreLabel)
        assertEquals(260, d.pages)
        assertEquals("A.2.14", d.locationLabel)
        assertEquals(2, d.copiesTotal)
        assertFalse(d.available)
        // Server-only data stays empty offline.
        assertNull(d.personalHistory)
        assertTrue(d.digitalFiles.isEmpty())
    }

    @Test fun offlineDetailWithoutAuthorHasNoAuthorEntry() {
        val d = syncBook.copy(author = null).toCached().toOfflineDetail()
        assertTrue(d.authors.isEmpty())
    }
}
