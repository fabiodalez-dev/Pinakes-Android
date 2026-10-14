package com.pinakes.app.staffe2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import com.pinakes.app.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The staff area shows only for admin/staff users on a server that advertises staff_catalog. */
class StaffAccessFlowTest : AppFlowTest() {

    @Test fun adminSeesTheStaffAreaAndTheCatalogScanButton() {
        launch(role = "admin")
        openTab(R.string.nav_profile)
        waitText(text(R.string.profile_action_staff))
        openTab(R.string.nav_catalog)
        waitFor(hasContentDescription(text(R.string.staff_scan)))
    }

    @Test fun staffRoleSeesTheStaffArea() {
        launch(role = "staff") { json("GET me", "me_staff") }
        openTab(R.string.nav_profile)
        waitText(text(R.string.profile_action_staff))
    }

    @Test fun standardUserSeesNeitherEntry() {
        launch(role = "utente") { json("GET me", "me_user") }
        openTab(R.string.nav_profile)
        waitText(text(R.string.profile_action_edit))
        assertFalse(exists(text(R.string.profile_action_staff)))
        openTab(R.string.nav_catalog)
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasContentDescription(text(R.string.staff_scan))).fetchSemanticsNodes().isEmpty())
    }

    @Test fun adminOnAServerWithoutStaffCatalogSeesNoStaffEntry() {
        launch(role = "admin", health = "health_no_staff")
        openTab(R.string.nav_profile)
        waitText(text(R.string.profile_action_edit))
        assertFalse(exists(text(R.string.profile_action_staff)))
        openTab(R.string.nav_catalog)
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasContentDescription(text(R.string.staff_scan))).fetchSemanticsNodes().isEmpty())
    }

    @Test fun roleDowngradedOnTheServerHidesTheStaffAreaAndUpdatesTheHeader() {
        // Stored role says admin, but GET /me (the authoritative read) now says utente.
        launch(role = "admin") { json("GET me", "me_user") }
        openTab(R.string.nav_profile)
        waitText("Ugo Admin")
        waitGone(hasText(text(R.string.profile_action_staff)))
        assertFalse(exists("Admin", substring = false))
    }
}
