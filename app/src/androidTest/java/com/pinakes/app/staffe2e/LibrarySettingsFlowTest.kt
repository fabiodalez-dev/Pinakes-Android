package com.pinakes.app.staffe2e

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextReplacement
import com.pinakes.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hub → Library: settings (modules, loans), patron types, rules matrix and closures calendar,
 * against bodies captured from a Pinakes 0.9.0 server (Mobile API 1.10.0).
 */
class LibrarySettingsFlowTest : AppFlowTest() {

    private val health = "s090_library_health"

    private fun replace(tag: String, value: String) {
        val matcher = hasSetTextAction() and hasTestTag(tag)
        waitFor(matcher)
        compose.onAllNodes(matcher).onFirst().performScrollToSafely().performTextReplacement(value)
        compose.waitForIdle()
    }

    @Test fun anAdministratorSwitchesAModuleOn() {
        launch(health = health) {
            json("GET staff/settings/modules", "s090_library_modules_admin")
            json("GET staff/settings/loans", "s090_library_loans_admin")
            json("GET staff/settings/cards", "s090_library_cards_admin")
            json("PUT staff/settings/modules", "s090_library_modules_saved")
        }
        openStaffHub()
        click(text(R.string.staff090_settings))
        waitText(text(R.string.staff090_library_tab_cards))
        click("Fornitori")
        clickTag("settings-save")
        assertEquals("""{"features":{"suppliers":true}}""", lastBody("PUT", "staff/settings/modules"))
        waitText(text(R.string.staff090_library_saved))
    }

    @Test fun staffSavesALoanRuleWithoutMoneyOrModules() {
        launch(role = "staff", health = health) {
            json("GET me", "me_staff")
            json("GET staff/settings/modules", "s090_library_modules_staff")
            json("GET staff/settings/loans", "s090_library_loans_staff")
            json("GET staff/settings/cards", "s090_library_cards_staff_disabled", status = 404)
            json("PUT staff/settings/loans", "s090_library_loans_staff")
        }
        openStaffHub()
        click(text(R.string.staff090_settings))
        waitText(text(R.string.staff090_library_modules_read_only))
        // Modules: no save action at all; no cards tab (404 feature_disabled for Staff).
        assertTrue(compose.onAllNodes(hasTestTag("settings-save")).fetchSemanticsNodes().isEmpty())
        assertFalse(exists(text(R.string.staff090_library_tab_cards)))
        click(text(R.string.staff090_library_tab_loans))
        replace("loan-max_renewals", "4")
        assertTrue(compose.onAllNodes(hasTestTag("loan-daily_fine_cents")).fetchSemanticsNodes().isEmpty())
        clickTag("settings-save")
        assertEquals("""{"max_renewals":4}""", lastBody("PUT", "staff/settings/loans"))
    }

    @Test fun anAdministratorCreatesAPatronType() {
        launch(health = health) {
            json("GET staff/circulation/types", "s090_library_types_two")
            json("POST staff/circulation/types", "s090_library_type_created", status = 201)
        }
        openStaffHub()
        click(text(R.string.staff090_circulation_rules))
        waitText("Ricercatori app")
        clickDescription(text(R.string.staff090_library_type_new))
        typeIntoTag("type-name", "Docenti")
        typeIntoTag("type-max_loans", "3")
        clickTag("type-save")
        assertEquals(
            """{"name":"Docenti","reservations_enabled":true,"active":true,"max_loans":3}""",
            lastBody("POST", "staff/circulation/types"),
        )
        assertEquals(36, api.recorded("POST", "staff/circulation/types").last().getHeader("Idempotency-Key").orEmpty().length)
    }

    @Test fun theRulesMatrixSavesOnlyTheChangedCell() {
        launch(health = health) {
            json("GET staff/circulation/types", "s090_library_types_two")
            json("GET staff/circulation/rules", "s090_library_rules")
            json("PUT staff/circulation/rules", "s090_library_rules_saved")
        }
        openStaffHub()
        click(text(R.string.staff090_circulation_rules))
        click(text(R.string.staff090_library_matrix))
        waitText(text(R.string.staff090_library_all_types).uppercase())
        clickTag("cell-0-dvd")
        typeIntoTag("cell-loan_duration_days", "7")
        clickTag("cell-apply")
        clickTag("rules-save")
        assertEquals("""{"cells":[{"type_id":0,"media":"dvd","loan_duration_days":7}]}""", lastBody("PUT", "staff/circulation/rules"))
        waitText(text(R.string.staff090_library_saved))
    }

    @Test fun aClosedDaySaysWhyAndWeeklyDaysAreSaved() {
        launch(health = health) {
            json("GET staff/calendar", "s090_library_calendar_full")
            json("PUT staff/calendar/weekdays", "s090_library_weekdays_saved")
        }
        openStaffHub()
        click(text(R.string.staff090_calendar))
        clickTag("calendar-day-2026-12-25")
        waitText(text(R.string.staff090_library_reason_closure, "Chiusura natalizia (prova app)"))
        clickInDialog(text(R.string.staff090_library_close))
        clickTag("weekday-6")
        clickTag("weekdays-save")
        assertEquals("""{"closed_weekdays":[0,6]}""", lastBody("PUT", "staff/calendar/weekdays"))
    }

    @Test fun staffLeavesAnAdministratorOnlyAreaAndKeepsTheRole() {
        launch(role = "staff", health = health) {
            json("GET me", "me_staff")
            json("GET staff/circulation/types", "s090_library_types_staff_refused", status = 403)
            json("GET staff/settings/modules", "s090_library_modules_staff")
        }
        openStaffHub()
        click(text(R.string.staff090_circulation_rules))
        // The refusal is checked against a route every staff member reads: the role stays.
        waitRequest("GET", "staff/settings/modules")
        waitText(text(R.string.staff_scan_subtitle))
        assertEquals("staff", deps.session().role.value)
    }
}
