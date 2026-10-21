package com.pinakes.app

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.AvailabilityDay
import com.pinakes.app.ui.screens.detail.LoanCalendar
import com.pinakes.app.ui.screens.detail.LoanRequestSheetContent
import com.pinakes.app.ui.theme.PinakesTheme
import org.junit.Assert.*
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Behaviour shared by the styled sheet and main's availability/fallback grid after merging #46. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoanCalendarRegressionTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }).around(compose)

    private val month = YearMonth.now().plusMonths(1)
    private val target = month.atDay(2)
    private var picked: String? = null
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun calendar(day: AvailabilityDay = AvailabilityDay(target.toString(), available = 1)) =
        AvailabilityCalendar(totalCopies = 2, earliestAvailable = target.toString(), days = listOf(day))

    private fun grid(data: AvailabilityCalendar = calendar(), fallback: Boolean = false) {
        compose.setContent { PinakesTheme {
            Box(Modifier.width(320.dp)) {
                LoanCalendar(data, selected = null, onSelect = { picked = it }, anyFutureDay = fallback)
            }
        } }
    }

    private fun day(date: LocalDate = target) = compose.onNodeWithTag("calendar-day-$date", useUnmergedTree = true)

    @Test fun aFreeDayCanBeRequested() {
        grid()
        day().performClick()
        compose.runOnIdle { assertEquals(target.toString(), picked) }
    }

    @Test fun aPartlyBookedDayCanStillBeRequested() {
        grid(calendar(AvailabilityDay(target.toString(), available = 1, loaned = 1, state = "partial")))
        day().performClick()
        compose.runOnIdle { assertEquals(target.toString(), picked) }
    }

    @Test fun aFullyBookedDayHasNoRequestAction() {
        grid(calendar(AvailabilityDay(target.toString(), available = 0, loaned = 2, state = "full")))
        day().assertHasNoClickAction()
    }

    @Test fun anUnavailableDateOverridesAStaleFreeEntry() {
        grid(calendar().copy(unavailableDates = listOf(target.toString())))
        day().assertHasNoClickAction()
    }

    @Test fun zeroFreeCopiesCannotBeRequestedEvenWhenLabelledFree() {
        grid(calendar(AvailabilityDay(target.toString(), available = 0, state = "free")))
        day().assertHasNoClickAction()
    }

    @Test fun aDayOutsideTheFetchedWindowHasNoRequestAction() {
        grid(calendar().copy(days = emptyList()))
        day().assertHasNoClickAction()
    }

    @Test fun aPastDayCannotBeRequestedDespiteServerAvailability() {
        val yesterday = LocalDate.now().minusDays(1)
        grid(AvailabilityCalendar(earliestAvailable = yesterday.toString(),
            days = listOf(AvailabilityDay(yesterday.toString(), available = 1))))
        day(yesterday).assertHasNoClickAction()
    }

    @Test fun fallbackLetsTheReaderRequestAnUnknownFutureDay() {
        grid(calendar().copy(days = emptyList()), fallback = true)
        day().performClick()
        compose.runOnIdle { assertEquals(target.toString(), picked) }
    }

    @Test fun fallbackDoesNotAdvertiseUnverifiedAvailabilityInALegend() {
        grid(fallback = true)
        for (label in listOf(R.string.book_loan_legend_available, R.string.book_loan_legend_limited, R.string.book_loan_legend_unavailable)) {
            compose.onNodeWithText(context.getString(label)).assertDoesNotExist()
        }
    }

    // ComponentActivity has no AppCompat delegate for Android 13's framework LocaleManager.
    // Exercise the supported pre-33 locale path, where AppCompat stores the chosen language.
    @Config(sdk = [32])
    @Test fun monthNamesFollowTheChosenAppLanguageInsteadOfTheDeviceLanguage() {
        val before = AppCompatDelegate.getApplicationLocales()
        try {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"))
            grid()
            val name = month.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)
                .replaceFirstChar { it.titlecase(Locale.FRENCH) }
            compose.onNodeWithText("$name ${month.year}").assertIsDisplayed()
        } finally {
            AppCompatDelegate.setApplicationLocales(before)
        }
    }

    @Test fun loadingLeavesCancelAvailableAndPreventsAnEmptyRequest() {
        compose.setContent { PinakesTheme {
            LoanRequestSheetContent(true, null, false, false, {}, { start, _ -> picked = start })
        } }
        compose.onNodeWithTag("loan-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("loan-cancel").assertIsEnabled()
        compose.onNodeWithTag("calendar-grid").assertDoesNotExist()
        assertNull(picked)
    }

    @Test fun availabilityArrivingAfterLoadingPreselectsTheServerSuggestedDay() {
        var data by mutableStateOf<AvailabilityCalendar?>(null)
        compose.setContent { PinakesTheme {
            LoanRequestSheetContent(data == null, data, false, false, {}, { start, _ -> picked = start })
        } }
        compose.runOnIdle { data = calendar() }
        compose.onNodeWithTag("loan-confirm").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(target.toString(), picked) }
    }

    @Test fun confirmingSendsTheReadersChoiceInsteadOfTheInitialSuggestion() {
        val next = target.plusDays(1)
        val data = calendar().copy(days = listOf(AvailabilityDay(target.toString(), available = 1),
            AvailabilityDay(next.toString(), available = 1)))
        compose.setContent { PinakesTheme {
            LoanRequestSheetContent(false, data, false, false, {}, { start, _ -> picked = start })
        } }
        day(next).performScrollTo().performClick()
        compose.onNodeWithTag("loan-confirm").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(next.toString(), picked) }
    }

    @Test fun fallbackSelectionIsConfirmedAsAnIsoDate() {
        val today = LocalDate.now()
        compose.setContent { PinakesTheme {
            LoanRequestSheetContent(false, null, true, false, {}, { start, _ -> picked = start })
        } }
        compose.onNodeWithTag("loan-confirm").assertIsNotEnabled()
        day(today).performScrollTo().performClick()
        compose.onNodeWithTag("loan-confirm").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(today.toString(), picked) }
    }

    @Test fun theButtonsStayReachableAndInsideTheSheetOnAShortNarrowScreen() {
        var dismissed = false
        compose.setContent { PinakesTheme {
            Box(Modifier.width(320.dp).height(240.dp)) {
                LoanRequestSheetContent(false, calendar(), false, false, { dismissed = true }, { _, _ -> })
            }
        } }
        compose.onNodeWithTag("loan-cancel").performScrollTo().assertIsDisplayed()
        val sheet = compose.onNodeWithTag("loan-sheet").getBoundsInRoot()
        val cancel = compose.onNodeWithTag("loan-cancel").getBoundsInRoot()
        assertTrue(cancel.left >= sheet.left && cancel.right <= sheet.right)
        compose.onNodeWithTag("loan-cancel").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
