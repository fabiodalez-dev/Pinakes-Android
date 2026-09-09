package com.pinakes.app.ui.screens.periodicals

import com.pinakes.app.R
import com.pinakes.app.data.model.Meta
import com.pinakes.app.data.model.PeriodicalIssueDetail
import com.pinakes.app.data.model.PeriodicalSummary
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.ui.common.UiState
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

    // ---- Truncated issue list ----

    @Test fun truncatedMetaRaisesTheBanner() {
        assertTrue(isTruncatedList(Meta(truncated = true)))
    }

    @Test fun explicitlyUntruncatedMetaDoesNotRaiseTheBanner() {
        assertFalse(isTruncatedList(Meta(truncated = false)))
    }

    @Test fun aServerThatOmitsTheFieldIsTreatedAsComplete() {
        // Older servers have no `truncated` key at all: absent must never read as true,
        // or every year would claim to be partial against an un-upgraded instance.
        assertFalse(isTruncatedList(Meta(truncated = null)))
        assertFalse(isTruncatedList(Meta(nextCursor = "c1")))
        assertFalse(isTruncatedList(null))
    }

    // ---- 404 classification: missing resource vs deactivated plugin ----

    private fun failure(status: Int, code: String = ErrorCodes.NOT_FOUND, message: String = "Not found.") =
        ApiResult.Failure(code = code, message = message, httpStatus = status)

    @Test fun a404IsRecognisedByStatusOrByCode() {
        assertTrue(isNotFoundFailure(failure(404)))
        // The envelope can carry the code with no HTTP status attached (apiCall maps a
        // body-level error with httpStatus = 0).
        assertTrue(isNotFoundFailure(failure(0, code = ErrorCodes.NOT_FOUND)))
    }

    @Test fun otherFailuresAreNotWorthAHealthProbe() {
        assertFalse(isNotFoundFailure(failure(500, code = ErrorCodes.SERVER_ERROR)))
        assertFalse(isNotFoundFailure(failure(0, code = ErrorCodes.NETWORK)))
        assertFalse(isNotFoundFailure(failure(403, code = ErrorCodes.FORBIDDEN)))
    }

    @Test fun a404WithHealthAlsoGoneMeansThePluginIsOff() {
        assertEquals(
            PeriodicalsFailure.Gone,
            periodicalsFailureKind(failure(404), goneConfirmed = true),
        )
    }

    @Test fun a404WithHealthStillUpMeansOnlyThisResourceIsMissing() {
        assertEquals(
            PeriodicalsFailure.NotFound,
            periodicalsFailureKind(failure(404), goneConfirmed = false),
        )
    }

    @Test fun aNon404NeverDegradesToGoneEvenIfTheProbeSaysSo() {
        // Guards the call site's short-circuit: confirmGone() must not be consulted for a
        // network blip, and even a stale true must not hide a retryable error.
        assertEquals(
            PeriodicalsFailure.Error,
            periodicalsFailureKind(failure(0, code = ErrorCodes.NETWORK), goneConfirmed = true),
        )
        assertEquals(
            PeriodicalsFailure.Error,
            periodicalsFailureKind(failure(500, code = ErrorCodes.SERVER_ERROR), goneConfirmed = false),
        )
    }

    // ---- Error state built from the classification ----

    private fun errorState(kind: PeriodicalsFailure) = periodicalsErrorState(
        failure = failure(404),
        kind = kind,
        genericRes = R.string.periodicals_issue_error,
        notFoundRes = R.string.periodicals_issue_not_found,
    )

    @Test fun notFoundDropsTheServerMessageSoTheLocalizedWordingWins() {
        val state = errorState(PeriodicalsFailure.NotFound)

        // resolvedMessage() prefers a non-blank message: leaving the server's bare
        // "Not found." would show that instead of "This issue no longer exists."
        assertEquals("", state.message)
        assertEquals(R.string.periodicals_issue_not_found, state.messageRes)
    }

    @Test fun goneUsesTheSectionWideWording() {
        val state = errorState(PeriodicalsFailure.Gone)

        assertEquals("", state.message)
        assertEquals(R.string.periodicals_gone_subtitle, state.messageRes)
    }

    @Test fun aGenericFailureKeepsTheServerMessageAndTheScreenFallback() {
        val state = periodicalsErrorState(
            failure = ApiResult.Failure(ErrorCodes.SERVER_ERROR, "Upstream exploded", 500),
            kind = PeriodicalsFailure.Error,
            genericRes = R.string.periodicals_issue_error,
            notFoundRes = R.string.periodicals_issue_not_found,
        )

        assertEquals("Upstream exploded", state.message)
        assertEquals(R.string.periodicals_issue_error, state.messageRes)
    }

    @Test fun everyClassifiedFailureCodeSurvivesIntoTheState() {
        // The code is what auth-expiry checks and telemetry key off: it must never be lost.
        PeriodicalsFailure.entries.forEach { kind ->
            val state: UiState.Error = errorState(kind)
            assertEquals(ErrorCodes.NOT_FOUND, state.code)
        }
    }
}
