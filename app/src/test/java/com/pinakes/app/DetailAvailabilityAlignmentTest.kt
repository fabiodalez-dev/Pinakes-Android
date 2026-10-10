package com.pinakes.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import com.pinakes.app.data.model.Availability
import com.pinakes.app.data.model.BookDetail
import com.pinakes.app.ui.screens.detail.DetailContent
import com.pinakes.app.ui.theme.PinakesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The availability heads its box (its dot) on the book page: "Available" / "On loan" starts where the
 * copies line and the loan button start, as on the web book page. It used to sit 8dp to the
 * right, inside a pill whose colour is the box's own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailAvailabilityAlignmentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun availabilityLinesUpWithTheCopiesAndTheLoanButton() {
        var available by mutableStateOf(true)
        compose.setContent {
            PinakesTheme {
                DetailContent(
                    BookDetail(
                        title = "Aligned book",
                        availability = Availability(
                            copiesTotal = 1,
                            copiesAvailable = if (available) 1 else 0,
                            loanableNow = available,
                            state = if (available) "available" else "on_loan",
                        ),
                    ),
                    wishlisted = false, reserveBusy = false,
                    canBorrow = true, showWishlist = true, showReviews = false,
                    onReserve = {}, onToggleWishlist = {}, onShowMessage = {},
                )
            }
        }
        for (state in listOf(true, false)) {
            compose.runOnIdle { available = state }
            compose.waitForIdle()
            fun left(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
            // The dot is what the eye reads as the start of the status line.
            val status = compose.onNode(hasTestTag("availability-dot") and hasAnyAncestor(hasTestTag("detail-availability")), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.left
            assertEquals("status vs copies line (available=$state)", left("detail-copies"), status, 1f)
            assertEquals("status vs loan button (available=$state)", left("detail-loan"), status, 1f)
        }
    }
}
