package com.pinakes.app.server090

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.R
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.Staff090LibraryCalendar
import com.pinakes.app.data.model.Staff090LibraryDay
import com.pinakes.app.data.model.Staff090LibraryLoans
import com.pinakes.app.data.model.Staff090LibraryModules
import com.pinakes.app.ui.screens.staff.s090.library.LibrarySettingsUiState
import com.pinakes.app.ui.screens.staff.s090.library.LoansForm
import com.pinakes.app.ui.screens.staff.s090.library.LoansTab
import com.pinakes.app.ui.screens.staff.s090.library.ModulesTab
import com.pinakes.app.ui.screens.staff.s090.library.MonthGrid
import com.pinakes.app.ui.theme.PinakesTheme
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * What the library settings screens show for each role, rendered from captured server bodies:
 * an administrator gets switches and inputs, Staff the plain values (hidden, never disabled).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Staff090LibraryUiTest {
    // Same RuleChain as ConsultationOnlyButtonTest: registers the host activity.
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }).around(compose)

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val switches = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    private fun <T> data(name: String, serializer: KSerializer<T>): T =
        json.decodeFromString(Envelope.serializer(serializer), javaClass.getResourceAsStream("/server090/library/$name.json")!!.bufferedReader().readText()).data!!

    private fun modules(name: String, onModule: (String, Boolean) -> Unit = { _, _ -> }) {
        val m = data(name, Staff090LibraryModules.serializer())
        val state = LibrarySettingsUiState(modules = m, moduleValues = m.modules.associate { it.key to it.stored }, retention = m.attendanceRetentionDays.toString())
        compose.setContent { PinakesTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ModulesTab(m, state, onModule, {}) } } }
    }

    @Test fun anAdministratorSwitchesModules() {
        var toggled: Pair<String, Boolean>? = null
        modules("s090_library_modules_admin") { key, on -> toggled = key to on }
        val count = compose.onAllNodes(switches).fetchSemanticsNodes().size
        assertEquals(data("s090_library_modules_admin", Staff090LibraryModules.serializer()).modules.size, count)
        compose.onNodeWithText("Fornitori").performScrollTo().performClick()
        assertEquals("suppliers" to true, toggled)
        compose.onNodeWithTag("retention-days").performScrollTo()
    }

    @Test fun staffSeesTheStateWithoutControls() {
        modules("s090_library_modules_staff")
        assertEquals(0, compose.onAllNodes(switches).fetchSemanticsNodes().size)
        compose.onNodeWithText(context.getString(R.string.staff090_library_modules_read_only)).assertExists()
        org.junit.Assert.assertTrue(compose.onAllNodesWithText(context.getString(R.string.staff090_library_on)).fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, compose.onAllNodesWithTag("retention-days").fetchSemanticsNodes().size)
    }

    @Test fun staffEditsLoanRulesButNotMoney() {
        val l = data("s090_library_loans_staff", Staff090LibraryLoans.serializer())
        val values = LoansForm.initial(l, Locale.ITALY)
        val state = LibrarySettingsUiState(loans = l, loanOriginal = values, loanValues = values)
        compose.setContent { PinakesTheme { Column(Modifier.verticalScroll(rememberScrollState())) { LoansTab(l, state) { _, _ -> } } } }
        compose.onNodeWithTag("loan-max_renewals").assertExists()
        compose.onNodeWithTag("loan-app_timezone").assertExists()
        assertEquals(0, compose.onAllNodesWithTag("loan-daily_fine_cents").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("loan-fine_grace_mode").fetchSemanticsNodes().size)
        compose.onNodeWithText(context.getString(R.string.staff090_library_fines_admin_only)).performScrollTo().assertExists()
        compose.onNodeWithText(context.getString(R.string.staff090_library_loan_daily_fine)).performScrollTo().assertExists()
    }

    @Test fun anAdministratorGetsTheMoneyFields() {
        val l = data("s090_library_loans_admin", Staff090LibraryLoans.serializer())
        val values = LoansForm.initial(l, Locale.ITALY)
        compose.setContent { PinakesTheme { Column(Modifier.verticalScroll(rememberScrollState())) { LoansTab(l, LibrarySettingsUiState(loans = l, loanOriginal = values, loanValues = values)) { _, _ -> } } } }
        compose.onNodeWithTag("loan-daily_fine_cents").performScrollTo().assertExists()
        compose.onNodeWithTag("loan-fine_grace_mode").performScrollTo().assertExists()
        assertEquals(0, compose.onAllNodesWithText(context.getString(R.string.staff090_library_fines_admin_only)).fetchSemanticsNodes().size)
    }

    @Test fun theMonthShowsEveryDayAndATapOpensIt() {
        val c = data("s090_library_calendar_full", Staff090LibraryCalendar.serializer())
        var opened: Staff090LibraryDay? = null
        compose.setContent { PinakesTheme { MonthGrid(c, onPrev = {}, onNext = {}, onDay = { opened = it }) } }
        assertEquals(31, compose.onAllNodes(SemanticsMatcher("day cell") { n ->
            n.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith("calendar-day-") == true
        }).fetchSemanticsNodes().size)
        compose.onNodeWithTag("calendar-day-2026-12-25").performClick()
        assertEquals("closure", opened?.reasons?.single()?.type)
    }
}
