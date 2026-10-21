package com.pinakes.app

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.AvailabilityDay
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.screens.detail.LoanRequestSheetContent
import com.pinakes.app.ui.theme.PinakesTheme
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
 * The loan-request sheet stops the return date the day before a fully-booked day inside the
 * library's loan duration, says so, sends that date as end_date, and explains a tapped red day.
 * Same rule as the website calendar and the server's LoanWindow (Mobile API 1.8.2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoanRequestWindowTest {
    private val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }).around(compose)

    private var pickedStart: String? = null
    private var pickedEnd: String? = null

    /** A single-copy title, free from 2030-03-04, fully booked on [full]. */
    private fun calendar(vararg full: String, duration: Int = 14) = AvailabilityCalendar(
        totalCopies = 1,
        earliestAvailable = "2030-03-04",
        loanDurationDays = duration,
        maxLoanDurationDays = 90,
        days = (1..40).map { i ->
            val date = java.time.LocalDate.parse("2030-03-04").plusDays(i.toLong() - 1).toString()
            if (date in full) AvailabilityDay(date = date, available = 0, loaned = 1, state = "full")
            else AvailabilityDay(date = date, available = 1, state = "free")
        },
    )

    private fun show(calendar: AvailabilityCalendar) {
        compose.setContent { PinakesTheme {
            LoanRequestSheetContent(false, calendar, false, false, {}, { start, end -> pickedStart = start; pickedEnd = end })
        } }
    }

    @Test fun aScheduledLoanInsideTheDurationShortensTheReturnDateAndSaysSo() {
        show(calendar("2030-03-09"))
        compose.onNodeWithText(context.getString(R.string.book_loan_period_range, DateFormat.date("2030-03-04"), DateFormat.date("2030-03-08")))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("loan-limit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.book_loan_limit_note, 4, DateFormat.date("2030-03-08"), DateFormat.date("2030-03-09")))
            .assertIsDisplayed()
    }

    @Test fun confirmingACappedRequestSendsTheShortenedReturnDate() {
        show(calendar("2030-03-09"))
        compose.onNodeWithTag("loan-confirm").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("2030-03-04", pickedStart)
            assertEquals("2030-03-08", pickedEnd)
        }
    }

    @Test fun withNoConflictTheServerKeepsItsDefaultAndNothingIsExplained() {
        show(calendar("2030-04-20"))
        compose.onNodeWithText(context.getString(R.string.book_loan_period_range, DateFormat.date("2030-03-04"), DateFormat.date("2030-03-18")))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("loan-limit").assertDoesNotExist()
        compose.onNodeWithTag("loan-confirm").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("2030-03-04", pickedStart)
            assertNull(pickedEnd)
        }
    }

    @Test fun tappingAFullyBookedDayExplainsWhyItCannotBePicked() {
        show(calendar("2030-03-09"))
        compose.onNodeWithTag("calendar-day-2030-03-09", useUnmergedTree = true).performScrollTo().performClick()
        val expected = context.getString(R.string.book_loan_blocked_day, DateFormat.date("2030-03-09")) + " " +
            context.getString(R.string.book_loan_limit_note, 4, DateFormat.date("2030-03-08"), DateFormat.date("2030-03-09"))
        compose.onNodeWithText(expected).performScrollTo().assertIsDisplayed()
        // The pick is unchanged: the blocked day never becomes the start.
        compose.onNodeWithTag("loan-confirm").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("2030-03-04", pickedStart) }
    }

    @Test fun aCommitmentOnTheVeryNextDayLeavesNoLoanAndDisablesTheRequest() {
        show(calendar("2030-03-05"))
        compose.onNodeWithText(context.getString(R.string.book_loan_no_window, DateFormat.date("2030-03-05")))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("loan-confirm").performScrollTo().assertIsNotEnabled()
    }

    @Test fun theMaximumDurationIsShownAndSentWithoutABookingExplanation() {
        show(calendar("2030-03-14", duration = 30).copy(maxLoanDurationDays = 7))
        compose.onNodeWithText(context.getString(R.string.book_loan_period_range, DateFormat.date("2030-03-04"), DateFormat.date("2030-03-11")))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("loan-limit").assertDoesNotExist()
        compose.onNodeWithTag("loan-confirm").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("2030-03-04", pickedStart)
            assertEquals("2030-03-11", pickedEnd)
        }
    }
}
