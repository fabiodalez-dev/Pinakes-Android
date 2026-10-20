package com.pinakes.app.staffe2e

import com.pinakes.app.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gender and tax code follow the library's "visible fields" setting. */
class ProfileFieldsFlowTest : AppFlowTest() {

    private fun openProfileEditor() {
        openTab(R.string.nav_profile)
        clickUntil(text(R.string.profile_action_edit), text(R.string.profile_edit_title))
        waitText(text(R.string.profile_first_name))
    }

    @Test fun hiddenFieldsAreAbsentFromTheProfileEditor() {
        launch {
            json("GET me", "me_fields_hidden")
            json("GET auth/registration-fields", "registration_fields_hidden")
        }
        openProfileEditor()
        assertFalse(exists(text(R.string.profile_tax_code)))
        assertFalse(exists(text(R.string.profile_gender)))
        // The birth date (never hideable) is still there.
        assertTrue(exists(text(R.string.profile_birth_date)))
    }

    @Test fun visibleFieldsArePresentInTheProfileEditor() {
        launch {
            json("GET me", "me_fields_visible")
            json("GET auth/registration-fields", "registration_fields_visible")
        }
        openProfileEditor()
        waitText(text(R.string.profile_tax_code))
        waitText(text(R.string.profile_gender))
    }

    @Test fun registrationNeverAsksForHiddenFields() {
        launch(health = "health_registration_hidden") { json("GET auth/registration-fields", "registration_fields_hidden") }
        // Sign out to reach the login screen, then open the registration form.
        deps.session().clearToken()
        clickUntil(text(R.string.login_create_account), text(R.string.register_title))
        assertFalse(exists(text(R.string.profile_tax_code)))
        assertFalse(exists(text(R.string.profile_gender)))
    }

    @Test fun birthDatePickerMonthFitsThePhoneScreen() {
        launch { json("GET me", "me_fields_visible") }
        openProfileEditor()
        click("1980-01-01")
        waitText(text(R.string.action_save))
        val width = context.resources.displayMetrics.widthPixels.toFloat()
        // Material's day cells carry the full date ("…January 15, 1980") as their text.
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("1980", substring = true) and androidx.compose.ui.test.hasClickAction())
                .fetchSemanticsNodes().size >= 28
        }
        val days = compose.onAllNodes(androidx.compose.ui.test.hasText("1980", substring = true) and androidx.compose.ui.test.hasClickAction())
            .fetchSemanticsNodes()
        days.forEach { n ->
            val b = n.boundsInWindow
            assertTrue("day cell outside the screen: $b", b.left >= 0f && b.right <= width && b.width > 0f)
        }
    }
}
