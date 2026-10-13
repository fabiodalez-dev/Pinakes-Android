package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline the circulation section is hidden (not disabled); the book tools stay; back online it returns. */
class StaffOfflineFlowTest : AppFlowTest() {

    @Test fun circulationDisappearsOfflineAndComesBack() {
        launch { json("GET staff/circulation/summary", "circulation_summary") }
        openStaffHub()
        waitText(text(R.string.staff_circulation_desk))
        setAirplaneMode(true)
        waitGone(androidx.compose.ui.test.hasText(text(R.string.staff_circulation_desk)), timeoutMs = 15_000)
        assertFalse(exists(text(R.string.staff_scan_copy)))
        assertFalse(exists(text(R.string.staff_loan_new)))
        waitText(text(R.string.staff_offline_circulation_hidden))
        assertTrue(exists(text(R.string.staff_books)))
        assertTrue(exists(text(R.string.staff_section_books).uppercase()))
        setAirplaneMode(false)
        waitText(text(R.string.staff_circulation_desk), timeoutMs = 20_000)
    }

    @Test fun anOpenDeskScreenClosesWhenTheDeviceGoesOffline() {
        launch { json("GET staff/circulation/summary", "circulation_summary") }
        openStaffHub()
        click(text(R.string.staff_circulation_desk))
        waitText(text(R.string.staff_circulation_today, 1, 2))
        setAirplaneMode(true)
        waitText(text(R.string.staff_offline_circulation_hidden), timeoutMs = 15_000)
    }

    @Test fun withoutTheFlagThereIsNoCirculationSection() {
        launch(health = "health_staff_phase1")
        openStaffHub()
        waitText(text(R.string.staff_books))
        assertFalse(exists(text(R.string.staff_circulation_desk)))
        assertFalse(exists(text(R.string.staff_offline_circulation_hidden)))
        assertFalse(exists(text(R.string.staff_section_books).uppercase()))
        assertFalse(exists(text(R.string.staff_section_plugins).uppercase()))
    }
}
