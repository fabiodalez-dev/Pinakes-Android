package com.pinakes.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.width
import androidx.test.platform.app.InstrumentationRegistry
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.AvailabilityDay
import com.pinakes.app.ui.screens.detail.LoanRequestSheet
import com.pinakes.app.ui.theme.PinakesTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class LoanRequestSheetUiTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.now()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

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
        widthDp: Float? = null,
    ) = compose.setContent {
        // widthDp: lay the sheet out as on a phone that many dp wide, whatever the emulator.
        val base = LocalDensity.current
        val density = widthDp?.let { Density(context.resources.displayMetrics.widthPixels / it, base.fontScale) } ?: base
        CompositionLocalProvider(LocalDensity provides density) { PinakesTheme {
            LoanRequestSheet(
                loading = false,
                calendar = calendar,
                fallback = fallback,
                submitting = submitting,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
            )
        } }
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

    @Test fun fallbackPickerKeepsItsSeventhColumnInsideTheSheet() {
        // 360dp, the narrowest common phone: Material's seven 48dp columns need the sheet's side padding.
        sheet(calendar = null, fallback = true, widthDp = 360f)
        // Pixels throughout: the dp helpers of the test rule use the device density, not the forced one.
        val sheetRight = compose.onNodeWithTag("loan-sheet").fetchSemanticsNode().boundsInRoot.right
        val days = compose.onAllNodes(
            // Material announces each day as one button whose text is the full date.
            SemanticsMatcher("a day of the month") { node ->
                node.config.getOrNull(SemanticsProperties.Role) == Role.Button &&
                    node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                        ?.let { Regex("\\b\\d{1,2}\\b").containsMatchIn(it) && it.contains(today.year.toString()) } == true
            },
        ).fetchSemanticsNodes()
        assertEquals("Every day of the month is laid out", today.lengthOfMonth(), days.size)
        // boundsInRoot is clipped by the sheet: a cut seventh column shows up as narrow days.
        // Each day must keep its 40dp circle in view (without the bleed, 24dp of it is left).
        val px = context.resources.displayMetrics.widthPixels / 360f
        val narrowest = days.minOf { it.boundsInRoot.width } / px
        assertTrue("A day is cut by the sheet: ${narrowest}dp of its 40dp circle in view", narrowest >= 39.5f)
        assertTrue("A day overflows the sheet", days.maxOf { it.boundsInRoot.right } <= sheetRight + 1f)
    }
}
