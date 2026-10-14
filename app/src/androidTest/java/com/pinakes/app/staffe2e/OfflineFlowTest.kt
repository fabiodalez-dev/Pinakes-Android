package com.pinakes.app.staffe2e

import androidx.compose.ui.test.hasText
import com.pinakes.app.R
import com.pinakes.app.ui.common.DateFormat
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Offline behaviour: cached answers with a banner, no loan affordances, no availability shown as
 * current, localized empty states and never the raw network exception.
 */
class OfflineFlowTest : AppFlowTest() {

    /** Server gone and airplane mode on: the device is really offline. */
    private fun goOffline() {
        api.shutdown()
        setAirplaneMode(true)
    }

    private fun assertNoRawNetworkError() {
        assertFalse(exists("Failed to connect", substring = true))
        assertFalse(exists("Unable to resolve", substring = true))
    }

    @Test fun offlineSearchShowsCachedResultsWithoutAvailabilityOrLoanActions() {
        launch()
        waitCatalogueCached()
        goOffline()
        openTab(R.string.nav_catalog)
        waitText(text(R.string.offline_results))
        waitText("Il barone rampante")
        assertFalse(exists(text(R.string.availability_available)))
        assertFalse(exists(text(R.string.availability_on_loan)))
        assertNoRawNetworkError()
    }

    @Test fun offlineDetailSaysLastKnownAndHasNoLoanButtons() {
        launch()
        waitCatalogueCached()
        goOffline()
        openTab(R.string.nav_catalog)
        click("Il barone rampante")
        waitText(text(R.string.offline_availability_last_known, DateFormat.dateTime("2026-10-13 10:00:00"), 1, 2))
        assertFalse(exists(text(R.string.book_request_loan)))
        assertFalse(exists(text(R.string.book_reserve)))
        assertNoRawNetworkError()
    }

    @Test fun libraryAndWishlistWithoutASavedCopyShowTheOfflineEmptyState() {
        launch()
        goOffline()
        openTab(R.string.nav_library)
        waitText(text(R.string.offline_no_copy_title))
        assertNoRawNetworkError()
        openTab(R.string.nav_wishlist)
        waitText(text(R.string.offline_no_copy_title))
        assertNoRawNetworkError()
    }

    @Test fun goingOfflineAfterAnOnlineLoadHidesLoanAffordances() {
        launch()
        openTab(R.string.nav_catalog)
        click("Il barone rampante")
        waitText(text(R.string.book_request_loan))
        setAirplaneMode(true)
        waitGone(hasText(text(R.string.book_request_loan)))
        waitText(text(R.string.offline_actions_unavailable))
        assertFalse(exists(text(R.string.availability_available)))
        // Back online: the page reloads with its loan action.
        setAirplaneMode(false)
        waitText(text(R.string.book_request_loan), 20_000)
    }
}
