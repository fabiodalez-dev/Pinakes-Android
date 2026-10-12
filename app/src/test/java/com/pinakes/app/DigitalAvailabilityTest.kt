package com.pinakes.app

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.data.model.*
import com.pinakes.app.ui.screens.detail.DetailContent
import com.pinakes.app.ui.screens.collections.WantedContributorCredits
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
import kotlinx.serialization.json.Json

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DigitalAvailabilityTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }).around(compose)

    private val pdf = DigitalAttachment("https://library.example/book.pdf")
    private fun show(book: BookDetail, onReserve: () -> Unit = {}) {
        compose.setContent { PinakesTheme {
            DetailContent(book, wishlisted = false, reserveBusy = false, canBorrow = true,
                showWishlist = false, showReviews = false, onReserve = onReserve,
                onToggleWishlist = {}, onShowMessage = {})
        } }
    }

    @Test fun digitalOnlyBookShowsDigitalAccessInsteadOfPhysicalReservation() {
        show(BookDetail(title = "Digital book", availability = Availability(state = "unavailable"), digitalAttachments = listOf(pdf)))
        compose.onNodeWithTag("detail-loan").assertDoesNotExist()
        compose.onNodeWithTag("detail-copies").assertDoesNotExist()
        compose.onNodeWithText("Digital edition").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Read").performScrollTo().assertHasClickAction()
    }

    @Test fun occupiedPhysicalCopiesRemainReservableWhenDigitalContentExists() {
        var clicks = 0
        show(BookDetail(title = "Physical and digital", availability = Availability(copiesTotal = 2, state = "on_loan"),
            digitalAttachments = listOf(pdf))) { clicks++ }
        compose.onNodeWithTag("detail-loan").performScrollTo().performClick()
        assertEquals(1, clicks)
        compose.onNodeWithText("Digital edition").assertDoesNotExist()
    }

    @Test fun zeroCopiesWithoutDigitalContentDoesNotOfferAReservation() {
        show(BookDetail(title = "No holdings", availability = Availability(state = "unavailable")))
        compose.onNodeWithTag("detail-loan").assertDoesNotExist()
        compose.onNodeWithText("Unavailable").performScrollTo().assertIsDisplayed()
    }

    @Test fun oldServerSingleEbookStillCountsAsDigitalAccess() {
        val old = BookDetail(hasEbook = true, ebookUrl = "https://library.example/book.pdf")
        org.junit.Assert.assertTrue(old.digitalOnly)
        org.junit.Assert.assertFalse(old.copy(ebookUrl = "javascript:bad").digitalOnly)
    }

    @Test fun wantedCreditsKeepTranslatorSeparateAndSupportOlderServers() {
        val book = Json.decodeFromString<WantedBook>("""{"id":133,"title":"Wanted","author":"Tomás Ibáñez","contributors":[{"id":1,"name":"Tomás Ibáñez","role":"principale"},{"id":2,"name":"Alice Zaffaroni","role":"traduttore"}]}""")
        compose.setContent { PinakesTheme { WantedContributorCredits(book.contributors) } }
        compose.onNodeWithText("Translator").assertIsDisplayed()
        compose.onNodeWithText("Alice Zaffaroni").assertIsDisplayed()
        compose.onNodeWithText("Tomás Ibáñez").assertDoesNotExist()
        assertEquals(emptyList<AuthorRef>(), Json.decodeFromString<WantedBook>("""{"id":1,"title":"Old server"}""").contributors)
    }
}
