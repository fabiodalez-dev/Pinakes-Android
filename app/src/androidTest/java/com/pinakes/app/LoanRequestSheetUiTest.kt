package com.pinakes.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.AvailabilityDay
import com.pinakes.app.ui.screens.detail.LoanRequestSheet
import com.pinakes.app.ui.screens.detail.LoanRequestSheetContent
import com.pinakes.app.ui.theme.PinakesTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class LoanRequestSheetUiTest {
    @get:Rule val compose = createComposeRule()

    // A fixed day, passed to the sheet as its today: a run that crosses midnight cannot move
    // the device clock away from the calendar built here.
    private val today = LocalDate.of(2030, 3, 4)

    /** Six weeks from today: mostly free, a partial day and a full one in the first week. */
    private fun calendar(earliest: String? = today.plusDays(1).toString()) = AvailabilityCalendar(
        totalCopies = 2,
        earliestAvailable = earliest,
        unavailableDates = listOf(today.plusDays(3).toString()),
        days = (0L until 42L).map { offset ->
            val date = today.plusDays(offset)
            when (offset) {
                2L -> AvailabilityDay(date.toString(), available = 1, loaned = 1, state = "partial")
                3L -> AvailabilityDay(date.toString(), available = 0, loaned = 2, state = "full")
                else -> AvailabilityDay(date.toString(), available = 2, state = "free")
            }
        },
    )

    private fun sheet(
        calendar: AvailabilityCalendar? = calendar(),
        fallback: Boolean = false,
        submitting: Boolean = false,
        onDismiss: () -> Unit = {},
        onConfirm: (String) -> Unit = {},
    ) = compose.setContent {
        PinakesTheme {
            LoanRequestSheet(
                loading = false,
                calendar = calendar,
                fallback = fallback,
                submitting = submitting,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                today = today,
            )
        }
    }

    @Test fun bothButtonsSpanTheSheetAndConfirmSendsTheEarliestDay() {
        var confirmed: String? = null
        var dismissed = false
        sheet(onDismiss = { dismissed = true }, onConfirm = { confirmed = it })

        val sheet = compose.onNodeWithTag("loan-sheet").getBoundsInRoot()
        val confirm = compose.onNodeWithTag("loan-confirm").assertIsDisplayed().assertIsEnabled().getBoundsInRoot()
        val cancel = compose.onNodeWithTag("loan-cancel").assertIsDisplayed().getBoundsInRoot()
        assertEquals("Confirm and cancel share one width", confirm.width.value, cancel.width.value, .5f)
        assertTrue("Buttons stay inside the sheet", confirm.left >= sheet.left && confirm.right <= sheet.right)
        assertTrue("Cancel sits under confirm", cancel.top >= confirm.bottom)

        compose.onNodeWithTag("loan-confirm").performClick()
        compose.runOnIdle { assertEquals(today.plusDays(1).toString(), confirmed) }
        compose.onNodeWithTag("loan-cancel").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun confirmWaitsForADay() {
        sheet(calendar = calendar(earliest = null))
        compose.onNodeWithTag("loan-confirm").assertIsNotEnabled()
    }

    @Test fun confirmIsDisabledWhileTheRequestIsSent() {
        sheet(submitting = true)
        compose.onNodeWithTag("loan-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("loan-cancel").assertIsEnabled()
    }

    @Test fun fallbackGridKeepsItsSeventhColumnInsideTheSheet() {
        // The sheet's content in a 360dp column, the narrowest common phone, on any emulator:
        // the Dialog itself is a window with the device density, so it cannot be narrowed here.
        compose.setContent {
            PinakesTheme {
                Box(Modifier.width(360.dp)) {
                    LoanRequestSheetContent(loading = false, calendar = null, fallback = true, submitting = false, onDismiss = {}, onConfirm = {}, today = today)
                }
            }
        }
        val sheet = compose.onNodeWithTag("loan-sheet").getBoundsInRoot()
        val month = java.time.YearMonth.from(today)
        val days = (1..month.lengthOfMonth()).map { day ->
            compose.onNodeWithTag("calendar-day-${month.atDay(day)}", useUnmergedTree = true).fetchSemanticsNode()
        }
        assertEquals("Every day of the month is laid out", today.lengthOfMonth(), days.size)
        // The fallback now uses the same weighted grid as the availability calendar.
        val density = compose.density.density
        val narrowest = days.minOf { it.boundsInRoot.width } / density
        assertTrue("A day is clipped by the sheet: ${narrowest}dp", narrowest >= 30f)
        assertTrue("A day overflows the sheet", days.maxOf { it.boundsInRoot.right } / density <= sheet.right.value + .5f)
    }
}
