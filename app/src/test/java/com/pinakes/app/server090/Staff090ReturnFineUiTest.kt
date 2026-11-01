package com.pinakes.app.server090

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.data.model.StaffLoanLateFine
import com.pinakes.app.ui.screens.staff.s090.patrons.rememberReturnFineState
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
import java.util.Locale

/**
 * The late fine block of the return form: the amount a return today charges, the waiver switch
 * and its mandatory reason; nothing at all when the loan accrues no fine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "en-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Staff090ReturnFineUiTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            Locale.setDefault(Locale.US)
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }).around(compose)

    private var sent: Pair<Boolean, String?>? = null

    private fun show(fine: StaffLoanLateFine?) {
        compose.setContent {
            PinakesTheme {
                val state = rememberReturnFineState(fine)
                Column {
                    state.Content()
                    Button(onClick = { sent = true to state.waiveReason() }, enabled = state.canConfirm, modifier = Modifier.testTag("confirm")) { Text("Return") }
                }
            }
        }
    }

    @Test fun theFineIsShownAndAWaiverNeedsItsReason() {
        // The body the server answered for a loan 4 days late at 0.50 a day.
        show(StaffLoanLateFine("EUR", automaticFines = true, accruingDays = 4, accruingCents = 200, chargedCents = 0))
        compose.onNodeWithTag("return-fine").assertExists()
        compose.onNodeWithText("Late fine: €2.00 (days late: 4)").assertExists()
        compose.onNodeWithTag("confirm").assertIsEnabled()
        compose.onNodeWithText("Waive the late fine").performClick()
        compose.onNodeWithTag("confirm").assertIsNotEnabled()
        compose.onNodeWithTag("return-waive-reason").performTextInput("First time")
        compose.onNodeWithTag("confirm").assertIsEnabled().performClick()
        assertEquals(true to "First time", sent)
    }

    @Test fun withoutAnAccruingFineNothingIsShownOrSent() {
        show(StaffLoanLateFine("EUR", automaticFines = true, accruingDays = 0, accruingCents = 0))
        compose.onNodeWithTag("return-fine").assertDoesNotExist()
        compose.onNodeWithTag("confirm").assertIsEnabled().performClick()
        assertNull(sent!!.second)
    }

    @Test fun anOlderServerWithoutTheFieldKeepsTheOldForm() {
        show(null)
        compose.onNodeWithTag("return-fine").assertDoesNotExist()
        compose.onNodeWithTag("confirm").assertIsEnabled()
    }
}
