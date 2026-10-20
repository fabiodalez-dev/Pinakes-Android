package com.pinakes.app

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.screens.detail.LoanRequestSheet
import com.pinakes.app.ui.screens.detail.loanReturnDate
import com.pinakes.app.ui.theme.PinakesTheme
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

/**
 * The loan-request dialog names the return date the server will set: the chosen start plus
 * the `loan_duration_days` of GET /catalog/books/{id}/availability (Mobile API 1.8.1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoanRequestReturnDateTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }).around(compose)

    private fun show(calendar: AvailabilityCalendar) {
        compose.setContent { PinakesTheme {
            LoanRequestSheet(loading = false, calendar = calendar, fallback = false, submitting = false,
                onDismiss = {}, onConfirm = {})
        } }
    }

    @Test fun returnDateIsTheStartPlusTheLoanDuration() {
        assertEquals("2030-03-18", loanReturnDate("2030-03-04", 14))
        assertEquals("2030-04-03", loanReturnDate("2030-03-04", 30))
        assertNull(loanReturnDate("2030-03-04", 0))
        assertNull(loanReturnDate("not a date", 14))
    }

    @Test fun availabilityCarriesTheLoanDurationAndOlderServersOmitIt() {
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(21, json.decodeFromString<AvailabilityCalendar>("""{"total_copies":1,"loan_duration_days":21}""").loanDurationDays)
        assertNull(json.decodeFromString<AvailabilityCalendar>("""{"total_copies":1}""").loanDurationDays)
    }

    @Test fun dialogShowsTheReturnDateForThePreselectedStart() {
        show(AvailabilityCalendar(totalCopies = 1, earliestAvailable = "2030-03-04", loanDurationDays = 14))
        compose.onNodeWithText("Start: ${DateFormat.date("2030-03-04")} · Return by: ${DateFormat.date("2030-03-18")}")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun withoutTheDurationTheDialogNamesTheStandardPeriod() {
        show(AvailabilityCalendar(totalCopies = 1, earliestAvailable = "2030-03-04"))
        compose.onNodeWithText("From ${DateFormat.date("2030-03-04")}, for the library’s standard loan period")
            .performScrollTo().assertIsDisplayed()
    }
}
