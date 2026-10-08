package com.pinakes.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.pinakes.app.data.model.*
import com.pinakes.app.ui.screens.collections.DonationContent
import com.pinakes.app.ui.screens.collections.DonationState
import com.pinakes.app.ui.screens.periodicals.StandaloneArticleContent
import com.pinakes.app.ui.theme.PinakesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CollectionsUiTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun unconfirmedDonationIsReadOnlyAndOffersARecoveryAction() {
        var retries = 0
        compose.setContent { PinakesTheme {
            DonationContent(DonationState(title = "Grüße 日本語", consent = true, uncertain = true),
                onEdit = { _, _ -> }, onConsent = {}, onSubmit = { retries++ }, onReload = {}, onFree = {}, onDone = {})
        } }
        compose.onNodeWithText(text(R.string.donation_book_title)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.donation_uncertain)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_retry)).performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test fun confirmedProposalHasADoneActionAndNoSecondSendButton() {
        var done = 0
        compose.setContent { PinakesTheme {
            DonationContent(DonationState(submitted = true), onEdit = { _, _ -> }, onConsent = {}, onSubmit = {}, onReload = {}, onFree = {}, onDone = { done++ })
        } }
        compose.onNodeWithText(text(R.string.donation_success)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.donation_send)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.donation_done)).performClick()
        assertEquals(1, done)
    }

    @Test fun articleCreditsOpenSharedAuthorWorksAndCiteDialogOffersOxford() {
        var selectedAuthor: Int? = null
        compose.setContent { PinakesTheme {
            StandaloneArticleContent(StandaloneArticle(title = "Article without a cover", authors = "Petersen, Hans Uwe",
                authorCredits = listOf(ArticleCredit(42, "Hans Uwe Petersen")), containerType = "antologia", editors = "Müller, A.",
                citations = listOf(ArticleCitation("apa", "APA 7", "APA reference"), ArticleCitation("oxford", "Oxford (Umeå)", "Oxford reference", "<i>Oxford reference</i>"))),
                onOpenPdf = {}, onOpenPeriodical = {}, onOpenIssue = {}, onFindWorks = { _, id -> selectedAuthor = id })
        } }
        compose.onNodeWithText("Hans Uwe Petersen").performScrollTo().performClick()
        assertEquals(42, selectedAuthor)
        compose.onNodeWithText(text(R.string.article_cite)).performScrollTo().performClick()
        compose.onNodeWithText("Oxford (Umeå)").performClick()
        compose.onNodeWithText("Oxford reference").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.citation_copy)).assertHasClickAction()
    }
}
