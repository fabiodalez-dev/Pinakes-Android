package com.pinakes.app.staff

import com.pinakes.app.ui.common.OfflinePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline, availability cannot be guaranteed: loan affordances are hidden, never disabled. */
class OfflinePolicyTest {

    @Test fun onlineWithLoansShowsLoanActions() {
        assertTrue(OfflinePolicy.showLoanActions(canBorrow = true, offline = false))
    }

    @Test fun offlineNeverShowsLoanActions() {
        assertFalse(OfflinePolicy.showLoanActions(canBorrow = true, offline = true))
        assertFalse(OfflinePolicy.showLoanActions(canBorrow = false, offline = true))
    }

    @Test fun withoutLoansFeatureNoLoanActions() {
        assertFalse(OfflinePolicy.showLoanActions(canBorrow = false, offline = false))
    }

    @Test fun cachedAvailabilityIsNeverPresentedAsCurrent() {
        assertTrue(OfflinePolicy.showCurrentAvailability(offline = false))
        assertFalse(OfflinePolicy.showCurrentAvailability(offline = true))
    }
}
