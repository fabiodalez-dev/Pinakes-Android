package com.pinakes.app.ui.screens.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression guards for the contextual relevance sort used by SearchViewModel. */
class SearchSortStateTest {

    @Test fun firstTextSwitchesTheDefaultNewestSortToRelevance() {
        val next = SearchUiState(sort = BookSort.NEWEST).withQuery("calvino")

        assertEquals("calvino", next.query)
        assertEquals(BookSort.RELEVANCE, next.sort)
    }

    @Test fun whitespaceDoesNotEnableRelevance() {
        val next = SearchUiState(sort = BookSort.NEWEST).withQuery("   ")

        assertEquals(BookSort.NEWEST, next.sort)
    }

    @Test fun clearingARelevantQueryReturnsToNewest() {
        val current = SearchUiState(query = "calvino", sort = BookSort.RELEVANCE)

        assertEquals(BookSort.NEWEST, current.withQuery("").sort)
        assertEquals(BookSort.NEWEST, current.withQuery("   ").sort)
    }

    @Test fun explicitNonDefaultSortSurvivesQueryEdits() {
        val empty = SearchUiState(sort = BookSort.TITLE_ASC).withQuery("calvino")
        val edited = empty.withQuery("calvino racconti")
        val cleared = edited.withQuery("")

        assertEquals(BookSort.TITLE_ASC, empty.sort)
        assertEquals(BookSort.TITLE_ASC, edited.sort)
        assertEquals(BookSort.TITLE_ASC, cleared.sort)
    }

    @Test fun explicitNewestDuringSearchSurvivesFurtherTyping() {
        val current = SearchUiState(query = "calvino", sort = BookSort.NEWEST)

        assertEquals(BookSort.NEWEST, current.withQuery("calvino racconti").sort)
    }

    @Test fun relevanceIsAvailableOnlyForMeaningfulText() {
        assertFalse(BookSort.RELEVANCE.isAvailableFor(""))
        assertFalse(BookSort.RELEVANCE.isAvailableFor("   "))
        assertTrue(BookSort.RELEVANCE.isAvailableFor("calvino"))
        assertTrue(BookSort.NEWEST.isAvailableFor(""))
        assertTrue(BookSort.TITLE_DESC.isAvailableFor(""))
    }
}
