package com.pinakes.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.AvailabilityDay
import com.pinakes.app.ui.screens.detail.LoanCalendar
import com.pinakes.app.ui.screens.detail.LoanRequestSheet
import com.pinakes.app.ui.theme.PinakesTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * The loan calendar's month grid fits a phone: 7 weekday columns and every day of the month
 * inside the screen (and inside a 320dp column), months navigable. The test device is 360dp
 * wide.
 */
class CalendarLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun calendar(): AvailabilityCalendar {
        val today = LocalDate.now()
        val days = (0 until 120).map { i ->
            val d = today.plusDays(i.toLong())
            AvailabilityDay(date = d.toString(), available = if (i % 9 == 0) 0 else 1, state = if (i % 9 == 0) "full" else "free")
        }
        return AvailabilityCalendar(totalCopies = 2, earliestAvailable = today.toString(), days = days)
    }

    private fun assertMonthInside(month: YearMonth, container: Rect) {
        compose.onNodeWithTag("calendar-weekdays").onChildren().assertCountEquals(7)
        for (day in 1..month.lengthOfMonth()) {
            val node = compose.onNodeWithTag("calendar-day-${month.atDay(day)}", useUnmergedTree = true).fetchSemanticsNode()
            val b = node.boundsInRoot
            assertTrue("day $day of $month outside ($b vs $container)",
                b.left >= container.left - 0.5f && b.right <= container.right + 0.5f && b.top >= container.top - 0.5f && b.width > 0f)
        }
    }

    @Test fun loanDialogMonthFitsThePhoneScreenAndNavigates() {
        compose.setContent { PinakesTheme { LoanRequestSheet(loading = false, calendar = calendar(), fallback = false, submitting = false, onDismiss = {}, onConfirm = { _, _ -> }) } }
        val metrics = context.resources.displayMetrics
        val screen = Rect(0f, 0f, metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
        val dialog = compose.onNodeWithTag("loan-dialog").fetchSemanticsNode().boundsInRoot
        assertTrue("dialog wider than the screen: $dialog", dialog.left >= 0f && dialog.right <= screen.right)
        val month = YearMonth.now()
        assertMonthInside(month, dialog)
        compose.onNodeWithContentDescription(context.getString(R.string.cd_next_month)).performClick()
        compose.waitForIdle()
        assertMonthInside(month.plusMonths(1), dialog)
        compose.onNodeWithContentDescription(context.getString(R.string.cd_prev_month)).performClick()
        compose.waitForIdle()
        assertMonthInside(month, dialog)
    }

    @Test fun fallbackDialogUsesTheSameFittingGrid() {
        compose.setContent { PinakesTheme { LoanRequestSheet(loading = false, calendar = null, fallback = true, submitting = false, onDismiss = {}, onConfirm = { _, _ -> }) } }
        val dialog = compose.onNodeWithTag("loan-dialog").fetchSemanticsNode().boundsInRoot
        assertMonthInside(YearMonth.now(), dialog)
    }

    @Test fun monthGridFitsANarrow320dpColumn() {
        compose.setContent { PinakesTheme { Box(Modifier.width(320.dp).testTag("narrow")) { LoanCalendar(calendar(), selected = null, onSelect = {}) } } }
        val column = compose.onNodeWithTag("narrow").fetchSemanticsNode().boundsInRoot
        assertMonthInside(YearMonth.now(), column)
        assertTrue(compose.onAllNodesHelper("calendar-grid"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesHelper(tag: String): Boolean =
        onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size == 1
}
