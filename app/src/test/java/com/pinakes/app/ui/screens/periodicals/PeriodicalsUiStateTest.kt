package com.pinakes.app.ui.screens.periodicals

import com.pinakes.app.data.model.PeriodicalIssueDetail
import com.pinakes.app.data.model.PeriodicalSummary
import com.pinakes.app.ui.components.AvailabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guards for the pure Periodicals UI-state functions (pattern: SearchSortStateTest —
 * the ViewModels are not tested directly).
 */
class PeriodicalsUiStateTest {

    private fun summary(id: Int, title: String = "Testata $id") =
        PeriodicalSummary(id = id, title = title, type = "rivista")

    // ---- Pagination merge ----

    @Test fun appendPageAddsNewItemsAndKeepsCursor() {
        val state = PeriodicalsUiState(
            items = listOf(summary(1), summary(2)),
            nextCursor = "c1",
            loadingMore = true,
        )

        val next = state.appendPage(listOf(summary(3), summary(4)), cursor = "c2")

        assertEquals(listOf(1, 2, 3, 4), next.items.map { it.id })
        assertEquals("c2", next.nextCursor)
        assertFalse(next.loadingMore)
        assertTrue(next.hasMore)
    }

    @Test fun appendPageDropsDuplicateIdsFromTheBoundary() {
        val state = PeriodicalsUiState(items = listOf(summary(1), summary(2)), nextCursor = "c1")

        // Cursor windows can overlap on a boundary row: the repeated id must not re-appear
        // (LazyColumn keys are the ids and must stay unique).
        val next = state.appendPage(listOf(summary(2), summary(3)), cursor = null)

        assertEquals(listOf(1, 2, 3), next.items.map { it.id })
        assertNull(next.nextCursor)
        assertFalse(next.hasMore)
    }

    @Test fun appendPageWithOnlyDuplicatesLeavesTheListUnchanged() {
        val state = PeriodicalsUiState(items = listOf(summary(1)), nextCursor = "c1")

        val next = state.appendPage(listOf(summary(1)), cursor = null)

        assertEquals(listOf(1), next.items.map { it.id })
    }

    // ---- Type filter ----

    @Test fun togglingATypeSelectsIt() {
        val next = PeriodicalsUiState().withTypeToggled("giornale")

        assertEquals("giornale", next.type)
    }

    @Test fun togglingTheActiveTypeClearsTheFilter() {
        val state = PeriodicalsUiState(type = "giornale")

        assertNull(state.withTypeToggled("giornale").type)
    }

    @Test fun togglingADifferentTypeReplacesTheFilter() {
        val state = PeriodicalsUiState(type = "giornale")

        assertEquals("fanzine", state.withTypeToggled("fanzine").type)
    }

    @Test fun queryEditKeepsTheTypeFilter() {
        val state = PeriodicalsUiState(type = "rivista").withQuery("domenica")

        assertEquals("domenica", state.query)
        assertEquals("rivista", state.type)
    }

    // ---- Issue status → badge mapping ----

    @Test fun ownedMapsToTheOkBadge() {
        assertEquals(AvailabilityStatus.Available, issueStatusBadge("posseduto"))
    }

    @Test fun missingAndLostMapToTheErrorBadge() {
        assertEquals(AvailabilityStatus.Overdue, issueStatusBadge("mancante"))
        assertEquals(AvailabilityStatus.Overdue, issueStatusBadge("smarrito"))
    }

    @Test fun damagedAndUnderRestorationMapToTheWarningBadge() {
        assertEquals(AvailabilityStatus.DueSoon, issueStatusBadge("danneggiato"))
        assertEquals(AvailabilityStatus.DueSoon, issueStatusBadge("in_restauro"))
    }

    @Test fun expectedAndUnknownStatusesMapToTheNeutralBadge() {
        assertEquals(AvailabilityStatus.Returned, issueStatusBadge("atteso"))
        assertEquals(AvailabilityStatus.Returned, issueStatusBadge(""))
        assertEquals(AvailabilityStatus.Returned, issueStatusBadge("qualcosa_di_nuovo"))
    }

    // ---- PDF action gating ----

    @Test fun pdfActionIsOfferedOnlyForANonBlankPublicUrl() {
        assertTrue(PeriodicalIssueDetail(id = 1, pdfUrl = "https://example.org/f/1.pdf").canOpenPdf)
        assertFalse(PeriodicalIssueDetail(id = 1, pdfUrl = null).canOpenPdf)
        assertFalse(PeriodicalIssueDetail(id = 1, pdfUrl = "").canOpenPdf)
        assertFalse(PeriodicalIssueDetail(id = 1, pdfUrl = "   ").canOpenPdf)
    }
}
