package com.pinakes.app.server090

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.data.model.Availability
import com.pinakes.app.data.model.BookDetail
import com.pinakes.app.ui.screens.detail.DetailContent
import com.pinakes.app.ui.screens.detail.ViewerRules
import com.pinakes.app.ui.theme.PinakesTheme
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

/**
 * `loanable=false` / `reservations_enabled=false` (Pinakes 0.9.0): the loan button stays on the
 * page, disabled, with the reason under it; unknown rules keep the usual, tappable button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConsultationOnlyButtonTest {
    // Same RuleChain as DetailAvailabilityAlignmentTest: registers the host activity so the test
    // also runs in the release unit tests (ui-test-manifest is a debug dependency).
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }).around(compose)

    private var taps = 0

    private fun show(available: Boolean, rules: ViewerRules?) {
        compose.setContent {
            PinakesTheme {
                DetailContent(
                    BookDetail(
                        title = "Reference book",
                        availability = Availability(
                            copiesTotal = 1, copiesAvailable = if (available) 1 else 0, loanableNow = available,
                            state = if (available) "available" else "on_loan",
                        ),
                    ),
                    wishlisted = false, reserveBusy = false,
                    canBorrow = true, showWishlist = false, showReviews = false,
                    onReserve = { taps++ }, onToggleWishlist = {}, onShowMessage = {},
                    rules = rules,
                )
            }
        }
    }

    @Test fun consultationOnlyDisablesTheButtonAndSaysWhy() {
        show(available = true, rules = ViewerRules(loanable = false))
        compose.onNodeWithTag("detail-loan").assertIsNotEnabled()
        compose.onNodeWithTag("detail-loan-reason", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("detail-loan").performClick()
        assertEquals(0, taps)
    }

    @Test fun aClosedWaitlistDisablesReserveWhenNoCopyIsFree() {
        show(available = false, rules = ViewerRules(reservationsEnabled = false))
        compose.onNodeWithTag("detail-loan").assertIsNotEnabled()
        compose.onNodeWithTag("detail-loan-reason", useUnmergedTree = true).assertExists()
    }

    @Test fun unknownRulesKeepTheUsualButton() {
        show(available = true, rules = null)
        compose.onNodeWithTag("detail-loan").assertIsEnabled()
        compose.onNodeWithTag("detail-loan-reason", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("detail-loan").performClick()
        assertEquals(1, taps)
    }
}
