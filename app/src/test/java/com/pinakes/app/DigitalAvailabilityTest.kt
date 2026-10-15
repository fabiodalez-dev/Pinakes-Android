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

    @Test fun supplementaryPdfDoesNotAdvertiseACompleteDigitalEdition() {
        val book = BookDetail(title = "Errata only", availability = Availability(state = "unavailable"),
            digitalAttachments = listOf(pdf.copy(kind = "supplement")))
        show(book)
        compose.onNodeWithText("Digital edition").assertDoesNotExist()
        compose.onNodeWithText("Unavailable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("detail-loan").assertDoesNotExist()
        org.junit.Assert.assertTrue(book.copy(digitalAttachments = book.digitalAttachments
            + pdf.copy(url = "https://library.example/full-book.pdf")).digitalOnly)
    }

    @Test fun principalAuthorAliasesDoNotAddARedundantAuthorSuffix() {
        show(BookDetail(title = "Credits", authors = listOf(
            AuthorRef(1, "Principal", "principale"), AuthorRef(2, "Italian alias", "autore"),
            AuthorRef(3, "English alias", "author"), AuthorRef(4, "Translator name", "traduttore"))))
        for (name in listOf("Principal", "Italian alias", "English alias")) {
            compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("$name · Author").assertDoesNotExist()
        }
        compose.onNodeWithText("Translator name · Translator").performScrollTo().assertIsDisplayed()
    }

    @Test fun oldServerSingleEbookStillCountsAsDigitalAccess() {
        val old = BookDetail(hasEbook = true, ebookUrl = "https://library.example/book.pdf")
        org.junit.Assert.assertTrue(old.digitalOnly)
        org.junit.Assert.assertFalse(old.copy(ebookUrl = "javascript:bad").digitalOnly)
    }

    // The app's own parser settings (NetworkModule.json): a closed file's url = null becomes "".
    private val api = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true }

    @Test fun closedFilesKeepTheirCardWithTheReasonInsteadOfAButton() {
        val book = api.decodeFromString<BookDetail>("""{"id":7,"title":"Access","availability":{"copies_total":1},
            "digital_attachments":[
              {"url":"https://library.example/uploads/digital/open.pdf?pk_exp=4102444800&pk_uid=3&pk_sig=ab","label":"Open edition","kind":"ebook","visibility":"public","access":"granted"},
              {"url":null,"label":"Readers edition","kind":"ebook","visibility":"users","access":"login_required"},
              {"url":null,"label":"Loan edition","kind":"audio","visibility":"loan","access":"loan_required"}]}""")
        // Two closed files share the same empty address: both stay listed.
        assertEquals(listOf("Open edition", "Readers edition", "Loan edition"), book.digitalFiles.map { it.label })
        show(book)
        compose.onNodeWithText("Sign in to open it").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("For readers who have this book on loan").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag("digital-locked").assertCountEquals(2)
        compose.onAllNodesWithText("Read").assertCountEquals(1)
        compose.onAllNodesWithText("Play").assertCountEquals(0)
    }

    @Test fun filesFromServersBeforeAccessControlAreOpen() {
        val book = api.decodeFromString<BookDetail>("""{"title":"Old","digital_attachments":[{"url":"https://library.example/a.pdf","label":"A","kind":"ebook"}]}""")
        val file = book.digitalFiles.single()
        org.junit.Assert.assertFalse(file.locked)
        org.junit.Assert.assertFalse(file.linkExpired())
    }

    @Test fun anExpiredSignedLinkFetchesTheRecordAgainInsteadOfOpening() {
        var refreshes = 0
        val messages = mutableListOf<String>()
        val expired = DigitalAttachment("https://library.example/uploads/digital/x.pdf?pk_exp=1000&pk_uid=3&pk_sig=ab", label = "Old link", access = "granted")
        org.junit.Assert.assertTrue(expired.linkExpired())
        org.junit.Assert.assertFalse(expired.copy(url = "https://library.example/uploads/digital/x.pdf?pk_exp=4102444800").linkExpired())
        compose.setContent { PinakesTheme {
            DetailContent(BookDetail(title = "Expired", digitalAttachments = listOf(expired)), wishlisted = false, reserveBusy = false,
                canBorrow = true, showWishlist = false, showReviews = false, onReserve = {}, onToggleWishlist = {},
                onShowMessage = { messages += it }, onRefreshLinks = { refreshes++ })
        } }
        compose.onNodeWithText("Read").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, refreshes)
        assertEquals(listOf("Link refreshed: tap again to open it."), messages)
    }

    @Test fun wantedCreditsKeepTranslatorSeparateAndSupportOlderServers() {
        val book = Json.decodeFromString<WantedBook>("""{"id":133,"title":"Wanted","author":"Tomás Ibáñez","contributors":[{"id":1,"name":"Tomás Ibáñez","role":"principale"},{"id":2,"name":"Alice Zaffaroni","role":"traduttore"}]}""")
        val aliases = listOf(AuthorRef(3, "Italian alias", "autore"), AuthorRef(4, "English alias", "author"),
            AuthorRef(5, "Coauthor", "co-autore"))
        compose.setContent { PinakesTheme { WantedContributorCredits(book.contributors + aliases) } }
        compose.onNodeWithText("Translator").assertIsDisplayed()
        compose.onNodeWithText("Alice Zaffaroni").assertIsDisplayed()
        compose.onNodeWithText("Tomás Ibáñez").assertDoesNotExist()
        for (alias in aliases) compose.onNodeWithText(alias.name).assertDoesNotExist()
        assertEquals(emptyList<AuthorRef>(), Json.decodeFromString<WantedBook>("""{"id":1,"title":"Old server"}""").contributors)
    }
}
